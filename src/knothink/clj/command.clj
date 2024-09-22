(ns knothink.clj.command
  (:gen-class)
  (:use org.httpkit.server)
  (:require [knothink.clj.util :refer :all]
            [knothink.clj.config :refer [config knothink-cat]]
            [knothink.clj.session :refer [gen-session check-or-new-password]]
            [hiccup2.core :as hic]
            [clj-jgit.porcelain :as jgit]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [tick.core :as t])
  (:import (clojure.lang IPersistentMap)
           (java.io FileNotFoundException)
           (java.nio.file StandardCopyOption)
           (java.util Date)))

;(defn asset-symlink-make [file]
;  (let [[name ext] (fs/split-ext file)]
;    (if-not (= ".txt" ext)
;      (let [target (str (piece-dir-path name) "/" file)
;            path (str (asset-dir-path name) "/" file)]
;        (fs/mkdirs (fs/parent path))
;        (fs/sym-link path target)))))

;(defn put-in-drawer []
;  (try
;    (doseq [file (fs/list-dir (str (:pieces @config) "/" knothink-cat))]
;      (when (fs/file? file)
;        (let [[name ext] (fs/split-ext file)
;              target (str (piece-dir-path name) "/" name ext)]
;          (fs/mkdirs (fs/parent target))
;          (fs/move file target StandardCopyOption/REPLACE_EXISTING)
;          (asset-symlink-make (str name ext)))))
;    "'moved'"
;    (catch Exception e (str "'" (.getMessage e) "'"))))

(defn piece-exist? [cat name]
  (and (not (empty? name))
       (fs/exists? (piece-file-path cat name))))

(defn piece-meta [cat name]
  (when (piece-exist? cat name)
    (->> (slurp (piece-file-path cat name))
         (re-find (re-pattern (str "(?s)" #"^(\{.*?\})")))
         first
         clojure.edn/read-string)))

(defn piece-content [cat name]
  (if (piece-exist? cat name)
    (-> (piece-file-path cat name)
        slurp
        chomp-meta
        chomp-whitespace)))

(defn piece-time [cat name]
  (if-let [path (piece-file-path cat name)]
    (if (fs/exists? path)
      (-> (fs/file path)
          .lastModified
          (Date.)
          (t/zoned-date-time)
          time-format))))

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

(defn pull-git []
  (try
    (jgit/with-credentials (@config :git)
                           (jgit/git-pull (jgit/load-repo (@config :pieces))))
    ;(put-in-drawer)
    ; TODO 변경된 asset에 대해 symlink 처리
    "'pulled'"
    (catch FileNotFoundException _
      (git-clone))))

(defn push-git []
  (try
    (jgit/with-credentials (@config :git)
                           (jgit/git-push (jgit/load-repo (@config :pieces))))
    "'pushed'"
    (catch FileNotFoundException _
      (git-clone))))

(defn commit-git []
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
  (when-not (empty? content)
    (str (hic/html [:pre [:code {:class "clojure"} content]]))))

(defn parse-text-page [content]
  (if-not (empty? content)
    (let [x (atom content)]
      (doseq [[grp ext param-str] (re-seq #"@([a-z]+)(?:\s+(.*?))@" content)]
        (let [grp-escape (escape-regex-char grp)
              params (vec (map #(str/replace % #"^\"|\"$" "")
                               (re-seq #"\".*?\"|[^\s]+" param-str)))]
          (if-let [fn (-> (str "knothink.clj.extension/fn-" ext) (symbol) (resolve))]
            (reset! x (str/replace @x
                                   (re-pattern grp-escape)
                                   (try (fn params)
                                        (catch Exception _ (format "error - %s" grp-escape)))))
            (println "fn load error - " *ns* (str "fn-" ext)))))
      (str/replace @x #"\r?\n" "<br />"))))

(defn login [cat title con]
  (if (check-or-new-password con (@config :password-file))
    (let [{:keys [session-id]} (gen-session)]
      {:redirect-info {:url     (generate-url cat title)
                       :cookies {"session-id" {:max-age 86400
                                               :path    "/"
                                               :value   session-id}}}})
    {:redirect-info {:url "/piece/who-a-u"}}))

(defn write-piece [cat title meta content]
  (let [path (piece-file-path cat title)
        meta (merge (if (empty? meta) {} meta)
                    (when-not (piece-exist? cat title) (:meta-init @config)))
        piece (str meta "\n" content)]
    (fs/mkdirs (fs/parent path))
    (spit path piece)))

(defn read-content [cat title]
  {:cat       cat
   :title     title
   :thing-con (str ".pw " (piece-content cat title))})

(defn write-content [cat title content]
  (write-piece cat title (piece-meta cat title) content)
  {:cat cat :title title :thing-con ""})

(defn add-content-head [cat title head]
  (let [content (str head "\n" (piece-content cat title))]
    (write-piece cat title (piece-meta cat title) content)
    {:cat cat :title title :thing-con ""}))

(defn add-content-tail [cat title tail]
  (let [content (str (piece-content cat title) "\n" tail)]
    (write-piece cat title (piece-meta cat title) content)
    {:cat cat :title title :thing-con ""}))

(defn read-meta [cat title]
  {:cat cat :title title :thing-con (str ".mw " (piece-meta cat title))})

(defn write-meta [cat title meta]
  (let [meta (if (empty? meta) "{}" meta)
        meta-map (read-string meta)]
    (if (instance? IPersistentMap meta-map)
      (do (write-piece cat title meta-map (piece-content cat title))
          {:cat cat :title title :thing-con ""})
      {:cat cat :title title :thing-con (str ".mw " meta)})))


(defn delete-piece [cat title]
  (let [path (piece-file-path cat title)]
    (fs/delete path)
    {:cat cat :title (:start-page @config) :thing-con ""}))

(defn move-piece [cat old new]
  (if (and (piece-exist? cat old)
           (not (piece-exist? cat new)))
    (let [content (piece-content cat old)]
      (write-content cat new content)
      (delete-piece cat old)
      {:cat cat :title new :thing-con ""})
    {:cat cat :title old :thing-con (str ".mv " new)}))

(defn user-command [cat title thing]
  (let [meta (piece-meta cat title)
        fn-name (:guest-input meta)
        prefix (str "@" fn-name " ")]
    (cond
      (and fn-name (empty? thing))
      {:cat cat :title title :thing-con prefix}

      (and fn-name (str/starts-with? thing prefix))
      (if-let [fn (resolve (symbol (str "knothink.clj.extension/fn-" fn-name)))]
        {:cat cat :title title :thing-con (fn [title (str/replace thing prefix "")])}
        {:cat cat title title :thing-con (str "no fn - " fn-name)})

      :else
      {:cat cat :title title :thing-con thing})))
