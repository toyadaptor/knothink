(defproject knothink "0.1.3-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/clojurescript "1.11.54"]
                 [clj-commons/fs "1.6.307"]
                 [environ "1.2.0"]
                 [http-kit "2.3.0"]
                 [hiccup "2.0.0-RC2"]
                 [ring/ring-core "1.12.0"]
                 [ring/ring-devel "1.11.0"]
                 [crypto-password "0.3.0"]
                 [tick/tick "0.7.5"]
                 [ring/ring-jetty-adapter "1.9.6"]
                 [metosin/reitit-ring "0.2.6"]
                 [clj-jgit "1.0.2"]
                 [biscuit "1.0.0"]]
  :source-paths ["src"]
  :plugins [[lein-environ "1.2.0"]
            [lein-cljsbuild "1.1.8"]
            [lein-ring "0.12.6"]
            [lein-pprint "1.3.2"]]
  :main knothink.clj.core
  :profiles {:uberjar {:aot :all}}
  :ring {:handler      knothink.clj.core/app-handler
         :auto-reload? true}
  :cljsbuild {:builds [{
                        :source-paths ["src"]
                        :compiler     {:output-to     "resources/public/assets/main.js"
                                       :optimizations :advanced
                                       }}]})