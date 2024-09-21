(ns knothink.clj.config
  (:require [environ.core :refer [env]]
            [me.raynes.fs :as fs]))

(def knothink-cat "knothink")
(def config (let [conf {:base-dir      (or (env :base-dir) "/tmp/knothink")
                        :password-file (str (env :base-dir) "/pw")
                        :pieces        (str (env :base-dir) "/pieces")
                        :assets        (str (env :base-dir) "/assets")
                        :git           {:login (env :git-user)
                                        :pw    (env :git-token)
                                        :repo  (env :git-repository)}
                        :meta-init     {:guest-input "comment"}
                        :start-page    "main"
                        :404-page      "4o4"}]
              (if-not (fs/exists? (:assets conf))
                (fs/mkdirs (:assets conf)))
              (atom conf)))

