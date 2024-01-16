(defproject knothink "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/clojurescript "1.11.54"]
                 [http-kit "2.3.0"]
                 [hiccup "2.0.0-RC2"]
                 [ring/ring-core "1.11.0"]
                 [ring/ring-devel "1.11.0"]
                 [crypto-password "0.3.0"]
                 [tick/tick "0.7.5"]

                 #_[clj-commons/fs "1.6.307"]]
  :source-paths ["src"]
  :plugins [[lein-cljsbuild "1.1.8"]
            [lein-ring "0.12.6"]]
  :main knothink.clj.main
  :ring {:handler      knothink.clj.core/app-handler
         :auto-reload? true}
  :cljsbuild {:builds [{
                        :source-paths ["src"]
                        :compiler     {:output-to     "resources/public/assets/main.js"
                                       :optimizations :advanced
                                       }}]})