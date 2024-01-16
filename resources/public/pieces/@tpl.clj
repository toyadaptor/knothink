[:html
  [:head
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:meta {:charset "UTF-8"}]
   [:link {:rel "shortcut" :icon "true" :href "/assets/favicon.ico"}]
   [:link {:rel "stylesheet" :href "https://cdn.jsdelivr.net/npm/bulma@0.9.4/css/bulma.min.css"}]
   [:script {:defer "true" :src "https://use.fontawesome.com/releases/v5.3.1/js/all.js"}]]
  [:body
   [:div.container.p-3
    [:header
     [:div.columns
      [:div.column.has-text-right
       [:p.title "knothink"]]]]
    [:div.columns
     [:div.column
      [:p.title.is-5 "__TITLE__"]
      [:div.content.mt-5 "__CONTENT__"]
      [:p [:small.has-next-gray "__TIME__"]]
      [:hr]
      "__THING__"]
     [:div.column]]
    [:footer
     [:p [:span.icon-text
          [:span.icon
           [:i.fas.fa-copyright]]
          [:span "toyadaptor"]]]]
    [:script {:src "/assets/main.js" :type "text/javascript"}]]]]