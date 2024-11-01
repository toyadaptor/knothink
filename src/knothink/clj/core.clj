(ns knothink.clj.core
  (:gen-class)
  (:use org.httpkit.server)
  (:require [knothink.clj.route :as r]
            [knothink.clj.loader :as loader]))

(defn -main [& _]
  (loader/init)
  (run-server r/app-handler
              {:port 8888}))

