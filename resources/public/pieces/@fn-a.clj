(defn fn-a [param]
  (format "<a href=\"/piece/%s\">%s</a>" param param))

(comment
  (fn-a "gogo"))
