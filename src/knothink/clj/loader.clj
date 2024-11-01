(ns knothink.clj.loader
  (:gen-class)
  (:use org.httpkit.server)
  (:require [knothink.clj.config :refer [config knothink-cat]]
            [knothink.clj.util :refer :all]
            [me.raynes.fs :as fs]))

(defn safe-eval [code]
  (try
    (binding [*ns* (the-ns 'knothink.clj.extension)]
      (refer 'clojure.core)
      (eval code))
    (catch Exception e
      (println "Error during evaluation:" e))))

(defn eval-piece [cat name]
  (try
    (println "* load -" name)
    (-> (piece-content cat name "clj")
        load-string
        safe-eval)
    (catch Exception e
      (println "Error processing piece:" (.getMessage e)))))

(defn load-fn
  ([cat]
   (doseq [f (fs/find-files (str (:pieces @config) "/" cat) #"^fn_.*\.clj")]
     (try
       (eval-piece cat (fs/name f))
       (catch Exception e
         (println "Error processing file:" (.getMessage e))))))
  ([cat name]
   (try
     (eval-piece cat (str "@fn-" name))
     (catch Exception e
       (println "Error processing file:" (.getMessage e))))))


(defn load-config-addition [knothink-cat]
  (let [path (format "%s/%s/@config.clj" (:pieces @config) knothink-cat)]
    (if-let [config-map (if (fs/exists? path)
                          (slurp path))]
      (reset! config (merge @config (read-string config-map))))))


(defn init []
  (create-ns 'knothink.clj.extension)
  (load-fn knothink-cat)
  (load-config-addition knothink-cat))
(comment
  (load-fn knothink-cat))

