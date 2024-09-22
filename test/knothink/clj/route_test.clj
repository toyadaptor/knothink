(ns knothink.clj.route-test
  (:require [clojure.test :refer :all])
  (:require [knothink.clj.route :refer :all]
            [knothink.clj.util :refer :all]))

(deftest url-path-test
  (is (= [nil nil] (parse-url-path "/")))
  (is (= [nil "ping"] (parse-url-path "/ping")))
  (is (= ["ping" "_"] (parse-url-path "/ping/")))
  (is (= ["ping" "pong"] (parse-url-path "/ping/pong")))
  (is (= ["ping" "pong"] (parse-url-path "/ping/pong/")))
  (is (= ["ping" "pong"] (parse-url-path "/ping/pong/123"))))


