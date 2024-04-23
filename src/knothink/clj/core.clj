(ns knothink.clj.core
  (:gen-class)
  (:use org.httpkit.server)
  (:require [clj-jgit.porcelain :as jgit]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.util.codec :refer [form-decode]]
            [ring.util.response :refer [redirect]]
            [environ.core :refer [env]]
            [crypto.password.scrypt :as scrypt]
            [tick.core :as t]
            [hiccup2.core :as hic])
  (:import (java.io FileNotFoundException)))

;todo
;* page 의 meta 정보 저장.
;* comment 를 core 에 넣을 것인가.
;* upload 를 특정 command 에 묶을 것인가. upload 경로와 이름 문제.
;* upload 파일을 검색하는 방법.
;* page 이름 변경과 link 문제.

(def config (atom {:base-dir        "/tmp/knothink"
                   :password-file   "/tmp/knothink/pw"
                   :resource-dir    "/tmp/knothink/resources"
                   :resource-pieces "/tmp/knothink/resources/pieces"
                   :resource-assets "/tmp/knothink/resources/assets"
                   :git             {:login (env :git-user)
                                     :pw    (env :git-token)
                                     :repo  (env :git-repository)}
                   :start-page      "main"}))

(def session (atom {:session-id nil
                    :expires    nil}))

(def default-response
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    nil})


(defn- rand-str [len]
  (apply str (take len (repeatedly #(get "0123456789abcdefghijklmnopqrstuvwxyz"
                                         (rand-int 36))))))

(defn- gen-session []
  (let [id (rand-str 50)]
    (reset! session {:session-id id})))

(defn check-or-new-password [raw password-file]
  (if-not (.exists (io/file password-file))
    (do
      (io/make-parents password-file)
      (with-open [w (io/writer password-file)]
        (.write w (scrypt/encrypt raw)))))
  (scrypt/check raw (slurp password-file)))

(defn check-login [session cookie]
  (and (contains? cookie "session-id")
       (not (empty? (:session-id session)))
       (= (:session-id session) (-> cookie (get "session-id") :value))))

(defn piece-path [name]
  (str (@config :resource-pieces) "/" name
       (if (str/starts-with? name "@")
         ".clj" ".txt")))

(defn piece-exist? [name]
  (if-not (empty? name)
    (let [path (piece-path name)]
      (.exists (io/file path)))
    false))

(defn piece-content [name]
  (if (piece-exist? name)
    (slurp (piece-path name))
    nil))

(defn piece-time [name]
  (let [path (piece-path name)]
    (str/replace (t/format (t/formatter "yyyyMMdd hhmmss")
                           (if (.exists (io/file path))
                             (-> (io/file path)
                                 .lastModified
                                 (java.util.Date.)
                                 (t/zoned-date-time))
                             (t/zoned-date-time (t/now))))
                 #"0" "o")))


(defn load-config []
  (if-let [config-map (piece-content "@config")]
    (reset! config (merge @config (read-string config-map)))))


(defn load-fn
  ([]
   (doseq [f (seq (.list (io/file (@config :resource-pieces))))]
     (if (str/starts-with? f "@fn")
       (println (-> f
                    (str/replace #"\..*" "")
                    (piece-content)
                    read-string
                    eval)))))
  ([name]
   (-> (piece-content (str "@fn-" name))
       read-string
       eval)))

(defn template []
  (let [p "@tpl"
        path (piece-path p)]
    (if (.exists (io/file path))
      (str (hic/html (clojure.edn/read-string (or (piece-content p)
                                                  ""))))
      (str (hic/html [:html
                      [:body
                       [:p "__TITLE__"]
                       [:p "__CONTENT__"]
                       [:p "__THING__"]]])))))

(defn template-thing-in []
  (let [p "@tpl-thing-in"
        path (piece-path p)]
    (if (.exists (io/file path))
      (str (hic/html (clojure.edn/read-string (or (piece-content p)
                                                  ""))))
      (str (hic/html [:form {:method "post"}
                      [:p [:textarea {:id "thing" :name "thing"}]]
                      [:p [:button {:type "submit" :id "submit"} "submit"]]])))))

(defn template-thing-out [{:keys [is-pw]}]
  (let [p "@tpl-thing-out"
        path (piece-path p)]
    (if (.exists (io/file path))
      (-> (str (hic/html (clojure.edn/read-string (piece-content p))))
          (str/replace #"__INPUT_TYPE__" (if is-pw "password" "text")))
      (str (hic/html [:form {:method "post"}
                      [:p [:input {:type (if is-pw "password" "text") :id "thing" :name "thing"}]]
                      [:p [:button {:type "submit" :id "submit"} "submit"]]])))))


(defn upload-copy [upload-info title]
  (doseq [[i {:keys [filename tempfile size]}] (map-indexed vector upload-info)]
    (if (and (< 0 size) (str/index-of filename "."))
      (io/copy (io/file tempfile)
               (io/file (str (@config :resource-assets) "/" title i
                             (str/replace filename #"^.*\." ".")))))))
(defn upload [multipart title]
  (let [file1 (get multipart "file1")]
    (upload-copy (if (map? file1) [file1] file1)
                 title)))

(defn- git-clone []
  (try
    (jgit/with-credentials (@config :git)
                           (jgit/git-clone (-> @config :git :repo)
                                           :branch "main"
                                           :dir (@config :resource-dir)))
    "'cloned'"
    (catch Exception e
      (str "'" (.getMessage e) "'"))))

(defn- git-pull []
  (try
    (jgit/with-credentials (@config :git)
                           (jgit/git-pull (jgit/load-repo (@config :resource-dir))))
    "'pulled'"
    (catch FileNotFoundException _
      (git-clone))))

(defn- git-push []
  (try
    (jgit/with-credentials (@config :git)
                           (jgit/git-push (jgit/load-repo (@config :resource-dir))))
    "'pushed'"
    (catch FileNotFoundException _
      (git-clone))))

(defn- git-add-and-commit []
  (try
    (let [repo (jgit/load-repo (@config :resource-dir))]
      (jgit/with-credentials (@config :git)
                             (jgit/git-add repo ".")
                             (jgit/git-commit repo
                                              "commit"
                                              :committer {:name  "knothink"
                                                          :email "knothink@knothink.com"}))
      "'committed'")
    (catch Exception e
      (str "'" (.getMessage e) "'"))))




(defn login [raw]
  (if (check-or-new-password raw (@config :password-file))
    (let [{:keys [session-id]} (gen-session)]
      (-> (redirect (str "/piece/" (@config :start-page)))
          (assoc :cookies {"session-id" {:max-age 86400
                                         :path    "/"
                                         :value   session-id}})))
    (-> (redirect "/piece/who-a-u"))))


(defn- parse-thing [thing]
  (if-not (empty? thing)
    (let [[_ cmd con] (re-matches #"(?s)^\.([a-z]{2})(?:\s+(.*?)\s*)?$" thing)]
      [cmd con])))

(defn- escape-regex-char [text]
  (if-not (empty? text)
    (str/replace text #"(\.|\+|\*|\?|\^|\$|\(|\)|\[|\]|\{|\}|\||\\)" "\\\\$1")
    nil))

(defn- escape-regex-html [text]
  (if-not (empty? text)
    (-> text
        (str/replace #"<" "&lt;")
        (str/replace #">" "&gt;"))
    nil))

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

(defn parse-page [p-title]
  (-> (template)
      (str/replace "__TITLE__" p-title)
      (str/replace "__CONTENT__" (if (str/starts-with? p-title "@")
                                   (or (parse-snail-page (piece-content p-title)) "' ')")
                                   (or (parse-text-page (piece-content p-title)) "' ')")))
      (str/replace "__TIME__" (piece-time p-title))))

(defn response [title thing thing-con]
  (-> default-response
      (assoc :body (-> (parse-page title)
                       (str/replace "__THING__" thing)
                       (str/replace "__THING_CON__" thing-con)))))


(defn cmd-logout []
  (-> (redirect (str "/piece/" (@config :start-page)))
      (assoc :cookies {"session-id" {:max-age 0
                                     :path    "/"
                                     :value   nil}})))
(defn cmd-goto [con]
  (redirect (str "/piece/" con)))


(defn cmd-re-read [p-title]
  (response p-title (template-thing-in) (str ".re " (piece-content p-title))))

(defn cmd-re-write [p-title con]
  (with-open [w (io/writer (piece-path p-title))]
    (.write w con))
  (response p-title (template-thing-in) ""))


(defn handler [req]
  (cond
    (str/starts-with? (req :uri) "/piece/")
    (let [title (-> (req :uri)
                    (form-decode)
                    (clojure.string/replace #"^/piece/" ""))
          thing (or (get (req :params) "thing") "")
          [cmd con] (parse-thing thing)]
      (if (check-login @session (req :cookies))
        (do
          (upload (req :multipart-params) title)
          (cond
            (= cmd "go") (cmd-goto con)
            (= cmd "bi") (cmd-logout)
            (= cmd "re") (if (empty? con)
                           (cmd-re-read title)
                           (cmd-re-write title con))
            (= cmd "gc") (response title (template-thing-in) (git-add-and-commit))
            (= cmd "gl") (response title (template-thing-in) (git-pull))
            (= cmd "gu") (response title (template-thing-in) (git-push))

            :else (if (and (nil? cmd)
                           (piece-exist? thing))
                    (cmd-goto thing)
                    (response title (template-thing-in) thing))))
        ; guest
        (cond
          (= cmd "hi") (login con)
          (= cmd "nn") (response title (template-thing-out {:is-pw true}) "")
          :else (response title (template-thing-out {:is-pw false}) thing))))
    :else (-> (redirect (str "/piece/" (@config :start-page))))
    :default {:status  404
              :headers {"Content-Type" "text/html"}
              :body    (piece-content "@404")}))

(def app-handler
  (-> handler
      wrap-cookies
      wrap-params
      (wrap-file (@config :resource-dir) {:prefer-handler? false})
      (wrap-multipart-params {:max-file-size  10240000
                              :max-file-count 15})))

(defn -main [& _]
  (load-fn)
  (load-config)
  (run-server app-handler
              {:port 8888}))

(comment
  (let [x (java.io.File/createTempFile "prefix" "suffix")]
    (.getPath x))

  (load-fn)
  (-main))