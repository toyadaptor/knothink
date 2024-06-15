(ns knothink.clj.config
  (:require [knothink.clj.util :refer [crc8-hash]]
            [environ.core :refer [env]]
            [me.raynes.fs :as fs]))

(def config (let [conf {:base-dir        (or (env :base-dir) "/tmp/knothink")
                        :password-file   (str (env :base-dir) "/pw")
                        :resource-dir    (str (env :base-dir) "/resources")
                        :resource-pieces (str (env :base-dir) "/resources/pieces")
                        :resource-assets (str (env :base-dir) "/resources/assets")
                        :git             {:login (env :git-user)
                                          :pw    (env :git-token)
                                          :repo  (env :git-repository)}
                        :start-page      "main"}]
              (if-not (fs/exists? (:resource-dir conf))
                (fs/mkdirs (:resource-dir conf)))
              (atom conf)))
@config
(defn load-config-addition []
  (let [name "@config"
        path (let [dir (crc8-hash name)]
               (format "%s/%s/%s.clj" (:resource-pieces @config) dir name))]
    (if-let [config-map (if (fs/exists? path)
                          (slurp path))]
      (reset! config (merge @config (read-string config-map))))))
