(ns knothink.clj.command
  (:gen-class)
  (:use org.httpkit.server)
  (:require [knothink.clj.util :refer :all]
            [knothink.clj.config :refer [config]]
            [knothink.clj.session :refer [gen-session check-or-new-password]]
            [clj-jgit.porcelain :as jgit]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [tick.core :as t])
  (:import (java.io FileNotFoundException)))

(def default-response
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    nil})


(defn piece-path [name]
  (let [dir (crc8-hash name)]
    (str (@config :pieces) "/" dir "/" name ".txt")))

(defn piece-exist? [name]
  (if-not (empty? name)
    (let [path (piece-path name)]
      (fs/exists? path))
    false))

(defn piece-content [name]
  (if (piece-exist? name)
    (slurp (piece-path name))
    nil))

(defn piece-time [name]
  (let [path (piece-path name)]
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
    (-> content
        (escape-regex-html)
        (str/replace #"\r?\n" "<br />"))))

(defn parse-text-page [content]
  (if-not (empty? content)
    (let [x (atom content)]
      (doseq [[grp ext param-str] (re-seq #"@([a-z]+)(?:\s+(.*))@" content)]
        (let [grp-escape (escape-regex-char grp)
              params (vec (map #(str/replace % #"^\"|\"$" "")
                               (re-seq #"\".*?\"|[^\s]+" param-str)))]
          (if-let [fn (-> (str "knothink.clj.core/fn-" ext) (symbol) (resolve))]
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


(defn cmd-login [{:keys [con]}]
  (if (check-or-new-password con (@config :password-file))
    (let [{:keys [session-id]} (gen-session)]
      {:redirect-info {:url     (str "/piece/" (@config :start-page))
                       :cookies {"session-id" {:max-age 86400
                                               :path    "/"
                                               :value   session-id}}}})
    {:redirect-info {:url "/piece/who-a-u"}}))

(defn cmd-logout []
  (println {:redirect-info {:url     (str "/piece/" (@config :start-page))
                            :cookies {"session-id" {:max-age 0
                                                    :path    "/"
                                                    :value   nil}}}})
  {:redirect-info {:url     (str "/piece/" (@config :start-page))
                   :cookies {"session-id" {:max-age 0
                                           :path    "/"
                                           :value   nil}}}})
(defn cmd-goto [{:keys [con]}]
  {:redirect-info {:url (str "/piece/" (str/replace con #" " "-"))}})


(defn cmd-re-read [title]
  {:title     title
   :thing-con (piece-content title)})

(defn cmd-re-write [title con]
  (let [path (piece-path title)]
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

(defn cmd-in-else [{:keys [title thing cmd]}]
  (if (and (nil? cmd)
           (piece-exist? thing))
    (cmd-goto thing)
    {:title     title
     :thing-con thing}))

(defn cmd-out-else [{:keys [title thing]}]
  {:title     title
   :thing-con thing})

