(ns knothink.clj.core
  (:gen-class)
  (:use org.httpkit.server)
  (:require [knothink.clj.route :as r]
            [knothink.clj.config :refer [config]]
            [knothink.clj.util :refer [crc8-hash]]
            [knothink.clj.command :refer [piece-content]]
            [clojure.string :as str]
            [me.raynes.fs :as fs]))



;todo
;* page 의 meta 정보 저장.
;* comment 를 core 에 넣을 것인가.
;* upload 를 특정 command 에 묶을 것인가. upload 경로와 이름 문제.
;* upload 파일을 검색하는 방법.
;* page 이름 변경과 link 문제.


(defn safe-eval [code]
  (try
    (binding [*ns* (the-ns 'knothink.clj.extension)]
      (refer 'clojure.core)
      (eval code))
    (catch Exception e
      (println "Error during evaluation:" e))))

(defn process-file [f]
  (let [name (fs/name f)]
    (when (str/starts-with? name "@fn-")
      (-> name
          (str/replace #"\..*" "")
          (piece-content)
          read-string
          safe-eval))))

(defn load-fn
  ([]
   (doseq [f (fs/find-files (:pieces @config) #"^@fn-.*")]
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


(defn load-config-addition []
  (let [name "@config"
        path (let [dir (crc8-hash name)]
               (format "%s/%s/%s.clj" (:pieces @config) dir name))]
    (if-let [config-map (if (fs/exists? path)
                          (slurp path))]
      (reset! config (merge @config (read-string config-map))))))

(defn -main [& _]
  (create-ns 'knothink.clj.extension)
  (load-fn)
  (load-config-addition)
  (run-server r/app-handler
              {:port 8888}))

