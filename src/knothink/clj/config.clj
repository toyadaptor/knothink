(ns knothink.clj.config
  (:require [knothink.clj.util :refer [crc8-hash]]
            [environ.core :refer [env]]
            [clojure.java.io :as io]))

(def config (atom {:base-dir        "/tmp/knothink"
                   :password-file   "/tmp/knothink/pw"
                   :resource-dir    "/tmp/knothink/resources"
                   :resource-pieces "/tmp/knothink/resources/pieces"
                   :resource-assets "/tmp/knothink/resources/assets"
                   :git             {:login (env :git-user)
                                     :pw    (env :git-token)
                                     :repo  (env :git-repository)}
                   :start-page      "main"}))


(defn load-config []
  (let [name "@config"
        path (let [dir (crc8-hash name)]
               (format "%s/%s/%s.clj" (:resource-pieces @config) dir name))]
    (if-let [config-map (if (.exists (io/file path))
                          (slurp path))]
      (reset! config (merge @config (read-string config-map))))))
