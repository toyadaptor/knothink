(ns knothink.clj.session
  (:gen-class)
  (:use org.httpkit.server)
  (:require [knothink.clj.util :refer :all]
            [clojure.java.io :as io]
            [crypto.password.scrypt :as scrypt]))

(def session (atom {:session-id nil
                    :expires    nil}))

(defn gen-session []
  (let [id (rand-str 50)]
    (reset! session {:session-id id
                     :expires    (+ (System/currentTimeMillis) (* 60 60 1000))})))

(defn check-or-new-password [raw password-file]
  (if-not (.exists (io/file password-file))
    (do
      (io/make-parents password-file)
      (with-open [w (io/writer password-file)]
        (.write w (scrypt/encrypt raw)))))
  (scrypt/check raw (slurp password-file)))


(defn check-login-session [session cookie]
  (and (contains? cookie "session-id")
       (not (empty? (:session-id session)))
       (= (:session-id session) (-> cookie (get "session-id") :value))
       (<= (System/currentTimeMillis) (:expires session))))

(defn check-login [cookie]
  (check-login-session @session cookie))
