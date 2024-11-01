(ns knothink.clj.util
  (:require [clojure.string :as str]
            [me.raynes.fs :as fs]
            [tick.core :as t]
            [knothink.clj.config :refer [config]])
  (:import (java.util Date)))

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
  (let [path (if (str/ends-with? path "/")
               (str path "_") path)
        [_ cat subject] (re-matches #"^/([^/]+)(?:/([^/]*))?.*$" path)]
    (if (str/blank? subject)
      [nil cat]
      [cat subject])))

(defn generate-url [cat title]
  (if (nil? cat)
    (str "/" title)
    (str "/" cat "/" title)))

(defn piece-file-path
  ([cat name] (piece-file-path cat name "txt"))
  ([cat name ext]
   (if (nil? cat)
     (str (@config :pieces) "/" name "." ext)
     (str (@config :pieces) "/" cat "/" name "." ext))))

(defn piece-exist?
  ([cat name] (piece-exist? cat name "txt"))
  ([cat name ext]
   (and (not (empty? name))
        (fs/exists? (piece-file-path cat name ext)))))

(defn piece-meta
  ([cat name] (piece-meta cat name "txt"))
  ([cat name ext]
   (when (piece-exist? cat name ext)
     (->> (slurp (piece-file-path cat name ext))
          (re-find (re-pattern (str "(?s)" #"^(\{.*?\})")))
          first
          clojure.edn/read-string))))

(defn piece-content
  ([cat name] (piece-content cat name "txt"))
  ([cat name ext]
   (if (piece-exist? cat name ext)
     (cond
       (= "clj" ext)
       (-> (piece-file-path cat name ext)
           slurp
           chomp-whitespace)
       (= "txt" ext)
       (-> (piece-file-path cat name ext)
           slurp
           chomp-meta
           chomp-whitespace)
       :else ""))))

(defn piece-time
  ([cat name] (piece-time cat name "txt"))
  ([cat name ext]
   (if-let [path (piece-file-path cat name ext)]
     (if (fs/exists? path)
       (-> (fs/file path)
           .lastModified
           (Date.)
           (t/zoned-date-time)
           time-format)))))

(comment
  (piece-file-path "knothink" "@config" "clj")
  (piece-content "knothink" "@config" "clj")
  (piece-time "knothink" "@config" "clj"))