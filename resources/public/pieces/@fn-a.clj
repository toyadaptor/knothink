; @a knothink@
;   => <a href="/piece/knothink">knothink</a>
; @a page knothink "no thing"@
;   => <a href="/piece/knothink">no thing</a>
(defn fn-a [[name text]]
  (format "<a href=\"/piece/%s\">%s</a>" name (if (empty? text)
                                                name text)))

