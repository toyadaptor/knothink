(ns knothink.clj.route
  (:gen-class)
  (:use org.httpkit.server)
  (:require [knothink.clj.util :refer :all]
            [knothink.clj.config :refer [config]]
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
        path (piece-file-path p)]
    (if (.exists (io/file path))
      (str (hic/html (clojure.edn/read-string (or (piece-content p)
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
        path (piece-file-path p)]
    (if (.exists (io/file path))
      (str (hic/html (clojure.edn/read-string (or (piece-content p)
                                                  ""))))
      (str (hic/html [:form {:method "post"}
                      [:p [:textarea {:id "thing" :name "thing"}]]
                      [:p [:button {:type "submit" :id "submit"} "submit"]]])))))

(defn template-thing-out []
  (let [p "@tpl-thing-out"
        path (piece-file-path p)]
    (if (.exists (io/file path))
      (str (hic/html (clojure.edn/read-string (piece-content p))))
      (str (hic/html [:form {:method "post"}
                      [:p [:input {:type "text" :id "thing" :name "thing" :autocomplete "off"}]]
                      [:p [:button {:type "submit" :id "submit"} "submit"]]])))))
(defn parse-page [title]
  (-> (template)
      (str/replace "__TITLE__" title)
      (str/replace "__CONTENT__" (if (str/starts-with? title "@")
                                   (or (parse-snail-page (piece-content title)) "' ')")
                                   (or (parse-text-page (piece-content title)) "' ')")))
      (str/replace "__TIME__" (or (piece-time title) (now-time-str)))))

(defn response [thing-tpl {:keys [title thing-con redirect-info]}]
  (if-not (nil? redirect-info)
    (-> (redirect (:url redirect-info))
        (assoc :cookies (-> redirect-info :cookies)))
    (-> default-response
        (assoc :body (-> (parse-page title)
                         (str/replace "__THING__" thing-tpl)
                         (str/replace "__THING_CON__" thing-con))))))



(defn handler [req]
  (cond
    (str/starts-with? (:uri req) "/piece/")
    (let [title (-> (:uri req)
                    (form-decode)
                    (clojure.string/replace #"^/piece/" "")
                    (clojure.string/replace #" " "-"))
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
                      (= cmd "go") {:redirect-info {:url (str "/piece/" (str/replace con #" " "-"))}}
                      (= cmd "bi") {:redirect-info {:url     (str "/piece/" title)
                                                    :cookies {"session-id" {:max-age 0
                                                                            :path    "/"
                                                                            :value   nil}}}}
                      (= cmd "mr") (read-meta title)
                      (= cmd "mw") (write-meta title con)
                      (= cmd "pr") (read-content title)
                      (= cmd "pw") (write-content title con)
                      (= cmd "ph") (add-content-head title con)
                      (= cmd "pt") (add-content-tail title con)
                      (= cmd "pm") (move-piece title con)
                      (= cmd "pd") (if (= title con)
                                     (delete-piece title)
                                     {:title title :thing-con thing})
                      (= cmd "gc") {:title title :thing-con (commit-git)}
                      (= cmd "gl") {:title title :thing-con (pull-git)}
                      (= cmd "gu") {:title title :thing-con (push-git)}
                      (= cmd "dr") {:title title :thing-con (put-in-drawer)}
                      :else (if (and (nil? cmd)
                                     (piece-exist? thing))
                              {:redirect-info {:url (str "/piece/" (str/replace thing #" " "-"))}}
                              {:title title :thing-con thing}))))

        ; guest
        (response (template-thing-out)
                  (cond
                    (= cmd "hi") (login title con)
                    :else (user-command title thing)))))

    (= "/" (:uri req))
    (-> (redirect (str "/piece/" (@config :start-page))))

    :default
    (-> (redirect (str "/piece/" (@config :404-page))))))

(def app-handler
  (-> handler
      wrap-cookies
      wrap-params
      (wrap-file (:assets @config) {:prefer-handler? false
                                    :allow-symlinks? true})
      (wrap-multipart-params {:max-file-size  10240000
                              :max-file-count 15})))
