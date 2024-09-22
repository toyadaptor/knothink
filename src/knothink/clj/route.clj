(ns knothink.clj.route
  (:gen-class)
  (:use org.httpkit.server)
  (:require [knothink.clj.util :refer :all]
            [knothink.clj.config :refer [config knothink-cat]]
            [knothink.clj.session :refer [check-login]]
            [knothink.clj.command :refer :all]
            [clojure.string :as str]
            [hiccup2.core :as hic]
            [clojure.java.io :as io]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.util.codec :refer [form-decode]]
            [ring.util.response :refer [redirect]]))


(def default-response
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    nil})

(defn parse-command [thing]
  (if-not (empty? thing)
    (let [[_ cmd con] (re-matches #"(?s)^\.([a-z]{2})(?:\s+(.*?)\s*)?$" thing)]
      [cmd con])))

(defn template []
  (let [p "@tpl"
        path (piece-file-path knothink-cat p)]
    (if (.exists (io/file path))
      (str (hic/html (clojure.edn/read-string (or (piece-content knothink-cat p)
                                                  ""))))
      (str (hic/html [:html
                      [:head
                       [:meta {:charset "utf-8"}]]
                      [:body
                       [:p "__TITLE__"]
                       [:p "__CONTENT__"]
                       [:p "__THING__"]]])))))

(defn template-thing-in []
  (let [p "@tpl-thing-in"
        path (piece-file-path knothink-cat p)]
    (if (.exists (io/file path))
      (str (hic/html (clojure.edn/read-string (or (piece-content knothink-cat p)
                                                  ""))))
      (str (hic/html [:form {:method "post"}
                      [:p [:textarea {:id "thing" :name "thing"}]]
                      [:p [:button {:type "submit" :id "submit"} "submit"]]])))))

(defn template-thing-out []
  (let [p "@tpl-thing-out"
        path (piece-file-path knothink-cat p)]
    (if (.exists (io/file path))
      (str (hic/html (clojure.edn/read-string (piece-content knothink-cat p))))
      (str (hic/html [:form {:method "post"}
                      [:p [:input {:type "text" :id "thing" :name "thing" :autocomplete "off"}]]
                      [:p [:button {:type "submit" :id "submit"} "submit"]]])))))
(defn parse-page [cat title]
  (-> (template)
      (str/replace "__CAT__" (or cat ""))
      (str/replace "__TITLE__" title)
      (str/replace "__CONTENT__" (if (str/starts-with? title "@")
                                   (or (parse-snail-page (piece-content cat title)) "' ')")
                                   (or (parse-text-page (piece-content cat title)) "' ')")))
      (str/replace "__TIME__" (or (piece-time cat title) (now-time-str)))))

(defn response [thing-tpl {:keys [cat title thing-con redirect-info]}]
  (if-not (nil? redirect-info)
    (-> (redirect (:url redirect-info))
        (assoc :cookies (-> redirect-info :cookies)))
    (-> default-response
        (assoc :body (-> (parse-page cat title)
                         (str/replace "__THING__" thing-tpl)
                         (str/replace "__THING_CON__" thing-con))))))





(defn handler [req]
  (if (str/ends-with? (:uri req) "/favicon.ico")            ; TODO
    default-response
    (let [[cat title] (parse-url-path (:uri req))]
      (cond
        (not (str/blank? title))
        (let [title (-> title
                        (form-decode)
                        (str/replace #" " "-"))
              thing (or (get (:params req) "thing") "")
              [cmd con] (parse-command thing)
              input {:title title
                     :thing thing
                     :cmd   cmd
                     :con   con}]
          (if (check-login (req :cookies))
            (do
              (upload (req :multipart-params) input)
              (response (template-thing-in)
                        (cond
                          (= cmd "go") (let [title (str/replace con #" " "-")]
                                         {:redirect-info {:url (if (str/starts-with? con "/")
                                                                 con
                                                                 (generate-url cat title))}})
                          (= cmd "bi") {:redirect-info {:url     (generate-url cat title)
                                                        :cookies {"session-id" {:max-age 0
                                                                                :path    "/"
                                                                                :value   nil}}}}
                          (= cmd "mr") (read-meta cat title)
                          (= cmd "mw") (write-meta cat title con)
                          (= cmd "pr") (read-content cat title)
                          (= cmd "pw") (write-content cat title con)
                          (= cmd "ph") (add-content-head cat title con)
                          (= cmd "pt") (add-content-tail cat title con)
                          (= cmd "pm") (move-piece cat title con)
                          (= cmd "pd") (if (= title con)
                                         (delete-piece cat title)
                                         {:title title :thing-con thing})
                          (= cmd "gc") {:cat cat :title title :thing-con (commit-git)}
                          (= cmd "gl") {:cat cat :title title :thing-con (pull-git)}
                          (= cmd "gu") {:cat cat :title title :thing-con (push-git)}
                          ;(= cmd "dr") {:cat cat :title title :thing-con (put-in-drawer)}
                          :else (if (and (nil? cmd)
                                         (piece-exist? cat thing))
                                  {:redirect-info {:url (str "/" cat "/" (str/replace thing #" " "-"))}}
                                  {:cat cat :title title :thing-con thing}))))

            ; guest
            (response (template-thing-out)
                      (cond
                        (= cmd "hi") (login cat title con)
                        :else (user-command cat title thing)))))

        :else
        (-> (redirect (str "/" (@config :start-page))))))))

(def app-handler
  (-> handler
      wrap-cookies
      wrap-params
      (wrap-file (:assets @config) {:prefer-handler? false
                                    :allow-symlinks? true})
      (wrap-multipart-params {:max-file-size  10240000
                              :max-file-count 15})))
