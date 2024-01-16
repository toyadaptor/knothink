(ns knothink.cljs.core)

(js/console.log "hi-")
(js/document.addEventListener
  "keydown"
  (fn [e]
    (if (and (.-ctrlKey e)
             (= "j" (.-key e)))
      (do
        (.select (js/document.getElementById "thing"))
        (window.scrollTo 0 document.body.scrollHeight)))

    (if (and (.-ctrlKey e)
             (= "k" (.-key e)))
      (.click (js/document.getElementById "submit")))))

