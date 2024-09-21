(ns knothink.clj.util-test
  (:require [clojure.test :refer :all]
            [knothink.clj.util :refer :all]
            [knothink.clj.config :refer [config]]
            [me.raynes.fs :as fs]))

(deftest crc8-table-lookup-returns-correct-value
  (is (= 0x07 (crc8-table-lookup 1))))

(deftest crc8-update-returns-updated-crc
  (is (= (crc8-update 0 0xFF) (crc8-table-lookup (bit-xor 0 0xFF)))))

(deftest crc8-calculates-correct-hash-for-empty-string
  (is (= 0 (crc8 ""))))

(deftest crc8-calculates-correct-hash-for-nonempty-string
  (is (= (crc8-update 0 (first (.getBytes "test" "UTF-8")))
         (crc8 "t"))))

;(deftest crc8-hash-returns-correct-format
;  (is (re-matches #"[0-9a-f]{2}" (crc8-hash "test"))))

(deftest rand-str-generates-string-of-correct-length
  (is (= 10 (count (rand-str 10)))))

(deftest escape-regex-char-escapes-special-characters
  (is (= "\\." (escape-regex-char "."))))

(deftest escape-regex-html-converts-html-char
  (is (= "&lt;&gt;" (escape-regex-html "<>"))))

(deftest piece-file-path-constructs-correct-path
  (let [cat "test"
        test-file "test-129283912"
        dir (str (@config :pieces) "/" cat)
        path1 (str (@config :pieces) "/" cat "/" test-file ".txt")
        path2 (str (@config :pieces) "/" test-file ".txt")]
    (fs/mkdir dir)
    (fs/touch path1)
    (fs/touch path2)
    (is (= (str (@config :pieces) "/" "cat" "/name.txt")
           (piece-file-path "cat" "name")))

    (is (= (str (@config :pieces) "/name.txt")
           (piece-file-path nil "name")))

    (fs/delete path1)
    (fs/delete path2)))








