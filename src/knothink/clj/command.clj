(ns knothink.clj.command
  (:gen-class)
  (:use org.httpkit.server)
  (:require [knothink.clj.util :refer :all]
            [knothink.clj.config :refer [config]]
            [knothink.clj.session :refer [gen-session check-or-new-password]]
            [hiccup2.core :as hic]
            [clj-jgit.porcelain :as jgit]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [tick.core :as t])
  (:import (java.io FileNotFoundException)
           (java.nio.file StandardCopyOption)))



(def default-response
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    nil})




(defn piece-file-path [name]
  (let [dir (crc8-hash name)]
    (str (@config :pieces) "/" dir "/" name ".txt")))
(defn piece-dir-path [name]
  (let [dir (crc8-hash name)]
    (str (@config :pieces) "/" dir)))

(defn asset-dir-path [name]
  (let [dir (crc8-hash name)]
    (str (@config :assets) "/asset/" dir)))

(defn asset-symlink-make [file]
  (let [[name ext] (fs/split-ext file)]
    (if-not (= ".txt" ext)
      (let [target (str (piece-dir-path name) "/" file)
            path (str (asset-dir-path name) "/" file)]
        (fs/mkdirs (fs/parent path))
        (fs/sym-link path target)))))

(defn piece-put-in-drawer []
  (try
    (doseq [file (filter #(fs/file? %) (fs/list-dir (:pieces @config)))]
      (let [[name ext] (fs/split-ext file)
            target (str (piece-dir-path name) "/" name ext)]
        (fs/mkdirs (fs/parent target))
        (fs/move file target StandardCopyOption/REPLACE_EXISTING)
        (asset-symlink-make (str name ext))))
    "'moved'"
    (catch Exception e
      (str "'" (.getMessage e) "'"))))


(defn piece-exist? [name]
  (if-not (empty? name)
    (fs/exists? (piece-file-path name))
    false))

(defn piece-content [name]
  (if (piece-exist? name)
    (slurp (piece-file-path name))
    nil))

(defn piece-time [name]
  (let [path (piece-file-path name)]
    (str/replace (t/format (t/formatter "yyyyMMdd hhmmss")
                           (if (fs/exists? path)
                             (-> (fs/file path)
                                 .lastModified
                                 (java.util.Date.)
                                 (t/zoned-date-time))
                             (t/zoned-date-time (t/now))))
                 #"0" "o")))

(defn safe-eval [code]
  (try
    (binding [*ns* (the-ns 'knothink.clj.ext)]
      (refer 'clojure.core)
      (eval code))
    (catch Exception e
      (println "Error during evaluation:" e))))

(defn process-file [f]
  (let [name (fs/name f)]
    (when (str/starts-with? name "@fn")
      (-> name
          (str/replace #"\..*" "")
          (piece-content)
          read-string
          safe-eval))))

(defn load-fn
  ([]
   (doseq [f (fs/find-files (:pieces @config) #"^@.*")]
     (try
       (println "load - " f)
       (process-file f)
       (catch Exception e
         (println "Error processing file:" (.getMessage e))))))
  ([name]
   (try
     (let [content (piece-content (str "@fn-" name))]
       (println "***" content)
       (-> (piece-content (str "@fn-" name))
           read-string
           safe-eval))
     (catch Exception e
       (println "Error processing file:" (.getMessage e))))))

(defn upload-copy [upload-info title]
  (doseq [[i {:keys [filename tempfile size]}] (map-indexed vector upload-info)]
    (if (and (< 0 size) (str/index-of filename "."))
      (fs/copy tempfile (str (@config :assets) "/" title i
                             (str/replace filename #"^.*\." "."))) ; TODO check
      #_(io/copy (fs/file tempfile)
                 (fs/file (str (@config :assets) "/" title i
                               (str/replace filename #"^.*\." ".")))))))
(defn upload [multipart {:keys [title]}]
  (let [file1 (get multipart "file1")]
    (upload-copy (if (map? file1) [file1] file1)
                 title)))

(defn git-clone []
  (try
    (jgit/with-credentials (@config :git)
                           (jgit/git-clone (-> @config :git :repo)
                                           :branch "main"
                                           :dir (@config :pieces)))
    "'cloned'"
    (catch Exception e
      (str "'" (.getMessage e) "'"))))

(defn git-pull []
  (try
    (jgit/with-credentials (@config :git)
                           (jgit/git-pull (jgit/load-repo (@config :pieces))))
    (piece-put-in-drawer)
    "'pulled'"
    (catch FileNotFoundException _
      (git-clone))))

(defn git-push []
  (try
    (jgit/with-credentials (@config :git)
                           (jgit/git-push (jgit/load-repo (@config :pieces))))
    "'pushed'"
    (catch FileNotFoundException _
      (git-clone))))

(defn git-add-and-commit []
  (try
    (let [repo (jgit/load-repo (@config :pieces))]
      (jgit/with-credentials (@config :git)
                             (jgit/git-add repo ".")
                             (jgit/git-commit repo
                                              "commit"
                                              :committer {:name  "noname"
                                                          :email "noname@knothink.com"}))
      "'committed'")
    (catch Exception e
      (str "'" (.getMessage e) "'"))))

(defn parse-snail-page [content]
  (if-not (empty? content)
    (str (hic/html [:pre [:code {:class "clojure"} content]]))

    ))

(defn parse-text-page [content]
  (if-not (empty? content)
    (let [x (atom content)]
      (doseq [[grp ext param-str] (re-seq #"@([a-z]+)(?:\s+(.*))@" content)]
        (let [grp-escape (escape-regex-char grp)
              params (vec (map #(str/replace % #"^\"|\"$" "")
                               (re-seq #"\".*?\"|[^\s]+" param-str)))]
          (if-let [fn (-> (str "knothink.clj.ext/fn-" ext) (symbol) (resolve))]
            (reset! x (str/replace @x
                                   (re-pattern grp-escape)
                                   (try
                                     (fn params)
                                     (catch Exception e
                                       (println e params)
                                       (format "error - %s" grp-escape)))))
            (println "fn load error - " *ns* (str "fn-" ext)))))
      (-> @x
          (str/replace #"\r?\n" "<br />")))))

(comment
  (load-fn "img")
  (-> "knothink.clj.ext/fn-img" (symbol) (resolve)))


(defn cmd-login [{:keys [con]}]                             ; keys 모두 빼자. 필요한것만 넘기기
  (if (check-or-new-password con (@config :password-file))
    (let [{:keys [session-id]} (gen-session)]
      {:redirect-info {:url     (str "/piece/" (@config :start-page))
                       :cookies {"session-id" {:max-age 86400
                                               :path    "/"
                                               :value   session-id}}}})
    {:redirect-info {:url "/piece/who-a-u"}}))

(defn cmd-logout []
  {:redirect-info {:url     (str "/piece/" (@config :start-page))
                   :cookies {"session-id" {:max-age 0
                                           :path    "/"
                                           :value   nil}}}})
(defn cmd-goto [{:keys [con]}]
  {:redirect-info {:url (str "/piece/" (str/replace con #" " "-"))}})


(defn cmd-re-read [title]
  {:title     title
   :thing-con (str ".re " (piece-content title))})

(defn cmd-re-write [title con]
  (let [path (piece-file-path title)]
    (fs/mkdirs (fs/parent path))
    (with-open [w (io/writer path)]
      (.write w con)))
  {:title     title
   :thing-con ""})

(defn cmd-rewrite [{:keys [title con]}]
  (if (empty? con)
    (cmd-re-read title)
    (cmd-re-write title con)))

(defn cmd-git-commit [{:keys [title]}]
  {:title     title
   :thing-con (git-add-and-commit)})

(defn cmd-git-pull [{:keys [title]}]
  {:title     title
   :thing-con (git-pull)})

(defn cmd-git-push [{:keys [title]}]
  {:title     title
   :thing-con (git-push)})

(defn cmd-put-in-drawer [{:keys [title]}]
  {:title     title
   :thing-con (piece-put-in-drawer)})

(defn cmd-in-else [{:keys [title thing cmd]}]
  (if (and (nil? cmd)
           (piece-exist? thing))
    (cmd-goto thing)
    {:title     title
     :thing-con thing}))

(defn cmd-out-else [{:keys [title thing]}]
  {:title     title
   :thing-con thing})
