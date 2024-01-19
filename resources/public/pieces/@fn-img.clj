(defn fn-img [[src alt]]
  (if (empty? src)
    "#img - empty src#"
    (format "<img src=\"/assets/%s\" alt=\"%s\" />" src (if (empty? alt)
                                                          (clojure.string/replace src #"^.*/" "")
                                                          alt))))
