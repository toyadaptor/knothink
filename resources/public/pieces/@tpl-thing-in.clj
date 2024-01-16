[:form {:method "post" :enctype "multipart/form-data"}
 [:div.field
  [:div.file
   [:label.file-label
    [:input.file-input {:type "file" :name "file1" :multiple true}]
    [:span.file-cta
     [:span.file-icon
      [:i.fas.fa-upload]]
     [:span.file-label "upload"]]]]
  [:div.control
   [:textarea#thing.textarea.mt-1 {:name "thing" :rows "10"} "__THING_CON__"]]
  [:div.control
   [:button#submit.button.small.is-fullwidth.mt-1 {:type "submit" :name "submit"} "submit"]]]]