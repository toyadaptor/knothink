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

(defn chomp-meta [content]
  (if-not (empty? content)
    (str/replace content (re-pattern (str "(?s)" #"^\{.*\}")) "")))

(defn chomp-whitespace [content]
  (if-not (empty? content)
    (str/replace content (re-pattern (str "(?s)" #"^(\r?\n|\t|\s)*|(\r?\n|\t|\s)*$")) "")))

(defn piece-content [name]
  (if (piece-exist? name)
    (-> (piece-file-path name)
        slurp
        chomp-meta
        chomp-whitespace)
    #_(slurp (piece-file-path name))
    nil))

(defn piece-meta [name]
  (if (piece-exist? name)
    (->> (slurp (piece-file-path name))
         (re-find (re-pattern (str "(?s)" #"^(\{.*?\})")))
         first
         (clojure.edn/read-string))
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
          (if-let [fn (-> (str "knothink.clj.extension/fn-" ext) (symbol) (resolve))]
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


(defn login [title con]
  (if (check-or-new-password con (@config :password-file))
    (let [{:keys [session-id]} (gen-session)]
      {:redirect-info {:url     (str "/piece/" title)
                       :cookies {"session-id" {:max-age 86400
                                               :path    "/"
                                               :value   session-id}}}})
    {:redirect-info {:url "/piece/who-a-u"}}))

(defn re-read [title]
  {:title     title
   :thing-con (str ".pw " (piece-content title))})

(defn re-write [title con]
  (let [con (str (piece-meta title) "\n" con)
        path (piece-file-path title)]
    (fs/mkdirs (fs/parent path))
    (with-open [w (io/writer path)]
      (.write w con)))
  {:title title :thing-con ""})

(defn re-add [title add]
  (let [con (str (piece-meta title) "\n" (piece-content title) "\n" add)
        path (piece-file-path title)]
    (fs/mkdirs (fs/parent path))
    (with-open [w (io/writer path)]
      (.write w con)))
  {:title title :thing-con ""})

(defn meta-read [title]
  {:title title :thing-con (str ".mw " (piece-meta title))})

(defn meta-write [title meta]
  (let [con (str meta "\n" (piece-content title))
        path (piece-file-path title)]
    (fs/mkdirs (fs/parent path))
    (with-open [w (io/writer path)]
      (.write w con)))
  {:title title :thing-con ""})

(defn piece-delete [title]
  (let [path (piece-file-path title)]
    (fs/delete path)
    {:title (:start-page @config) :thing-con ""}))


(defn piece-move [old new]
  (if (and (piece-exist? old)
           (not (piece-exist? new)))
    (let [content (piece-content old)]
      (re-write new content)
      (piece-delete old)
      {:title new :thing-con ""})
    {:title old :thing-con (str ".mv " new)}))


(defn user-command [title thing]
  (let [meta (piece-meta title)
        fn-name (:guest-input meta)
        prefix (str "@" fn-name " ")]
    (cond
      (and fn-name (empty? thing))
      {:title title :thing-con prefix}

      (and fn-name (str/starts-with? thing prefix))
      (if-let [fn (resolve (symbol (str "knothink.clj.extension/fn-" fn-name)))]
        {:title title :thing-con (fn [title (str/replace thing prefix "")])}
        {:title title :thing-con (str "no fn - " fn-name)})

      :else
      {:title title :thing-con thing})))


