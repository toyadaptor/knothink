(ns knothink.clj.config
  (:require [knothink.clj.util :refer [crc8-hash]]
            [environ.core :refer [env]]
            [me.raynes.fs :as fs]))

(def config (let [conf {:base-dir        (or (env :base-dir) "/tmp/knothink")
                        :password-file   (str (env :base-dir) "/pw")
                        :pieces (str (env :base-dir) "/pieces")
                        :assets (str (env :base-dir) "/assets")
                        :git             {:login (env :git-user)
                                          :pw    (env :git-token)
                                          :repo  (env :git-repository)}
                        :start-page      "main"}]
              (if-not (fs/exists? (:assets conf))
                (fs/mkdirs (:assets conf)))
              (atom conf)))
@config
(defn load-config-addition []
  (let [name "@config"
        path (let [dir (crc8-hash name)]
               (format "%s/%s/%s.clj" (:pieces @config) dir name))]
    (if-let [config-map (if (fs/exists? path)
                          (slurp path))]
      (reset! config (merge @config (read-string config-map))))))
