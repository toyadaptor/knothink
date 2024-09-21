(ns knothink.clj.util
  (:require [clojure.string :as str]
            [tick.core :as t]
            [knothink.clj.config :refer [config]]))

(defn rand-str [len]
  (apply str (take len (repeatedly #(get "0123456789abcdefghijklmnopqrstuvwxyz"
                                         (rand-int 36))))))

(defn escape-regex-char [text]
  (if-not (empty? text)
    (str/replace text #"(\.|\+|\*|\?|\^|\$|\(|\)|\[|\]|\{|\}|\||\\)" "\\\\$1")
    nil))

(defn escape-regex-html [text]
  (if-not (empty? text)
    (-> text
        (str/replace #"<" "&lt;")
        (str/replace #">" "&gt;"))
    nil))

(defn piece-file-path [cat name]
  (if (nil? cat)
    (str (@config :pieces) "/" name ".txt")
    (str (@config :pieces) "/" cat "/" name ".txt")))

;(defn piece-dir-path [name]
;  (let [dir (crc8-hash name)]
;    (str (@config :pieces) "/" dir)))

;(defn asset-dir-path [name]
;  (let [dir (crc8-hash name)]
;    (str (@config :assets) "/asset/" dir)))

(defn chomp-meta [content]
  (if-not (empty? content)
    (str/replace content (re-pattern (str "(?s)" #"^\{.*\}")) "")))

(defn chomp-whitespace [content]
  (if-not (empty? content)
    (str/replace content (re-pattern (str "(?s)" #"^(\r?\n|\t|\s)*|(\r?\n|\t|\s)*$")) "")))

(defn time-format [zoned-date-time]
  (-> (t/format (t/formatter "yyyyMMdd hhmmss")
                zoned-date-time)
      (str/replace "0" "o")))

(defn now-time-str []
  (-> (t/zoned-date-time (t/now))
      time-format))


(defn parse-url-path [path]
  (let [[_ cat subject] (re-matches #"^/([^/]+)(?:/([^/]*))?.*$" path)]
    (if (str/blank? subject)
      [nil cat]
      [cat subject])))