(ns knothink.clj.core
  (:gen-class)
  (:use org.httpkit.server)
  (:require [knothink.clj.route :as r]
            [knothink.clj.config :refer [config knothink-cat]]
            [knothink.clj.command :refer [piece-content]]
            [clojure.string :as str]
            [me.raynes.fs :as fs]))



(defn safe-eval [code]
  (try
    (binding [*ns* (the-ns 'knothink.clj.extension)]
      (refer 'clojure.core)
      (eval code))
    (catch Exception e
      (println "Error during evaluation:" e))))

(defn process-file [cat f]
  (let [name (fs/name f)]
    (when (str/starts-with? name "@fn-")
      (-> (piece-content cat (str/replace name #"\..*" ""))
          read-string
          safe-eval))))

(defn load-fn
  ([cat]
   (doseq [f (fs/find-files (str (:pieces @config) "/" cat) #"^@fn-.*")]
     (try
       (println "load - " f)
       (process-file cat f)
       (catch Exception e
         (println "Error processing file:" (.getMessage e))))))
  ([cat name]
   (try
     (let [content (piece-content cat (str "@fn-" name))]
       (println "***" content)
       (-> (piece-content cat (str "@fn-" name))
           read-string
           safe-eval))
     (catch Exception e
       (println "Error processing file:" (.getMessage e))))))


(defn load-config-addition []
  (let [path (format "%s/%s/@config.clj" (:pieces @config) knothink-cat)]
    (if-let [config-map (if (fs/exists? path)
                          (slurp path))]
      (reset! config (merge @config (read-string config-map))))))

(defn -main [& _]
  (create-ns 'knothink.clj.extension)
  (load-fn knothink-cat)
  (load-config-addition)
  (run-server r/app-handler
              {:port 8888}))

(comment
  (load-fn))

