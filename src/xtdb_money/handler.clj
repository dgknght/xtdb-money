(ns xtdb-money.handler
  (:require [clojure.tools.logging :as log]
            [hiccup2.core :as h]
            [reitit.ring :as ring]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [co.deps.ring-etag-middleware :refer [wrap-file-etag]]
            [xtdb-money.icons :refer [icon]]))

(defn- mount-point []
  [:div#app
   [:header.p-3.mb-3.border-bottom
    [:div.container
     [:div.d-flex.flex-wrap.align-items-center.justify-content-center.justify-content-lg-start
      [:a.d-flex.align-items-center.mb-2.mb-lg-0.link-body-emphasis.text-decoration-none.me-2
       {:href "/"}
       (icon :cash-coin :size :medium)]
      [:ul.nav.col-12.col-lg-auto.me-lg-auto.mb-2.mb-lg-0.justify-content-center
       [:li [:a.nav-link.px-2.link-secondary {:href "/about"} "About The App"]]]
      [:form.col-12.col-lg-auto.mb-2.mb-lg-0.me-lg-3
       [:input.form-control {:type :text
                             :placeholder "Search..."
                             :aria-label "Search"}]]
      [:div.dropdown.text-end
       [:a.d-block.link-body-emphasis.text-decoration-none.dropdown-toggle
        {:data-bs-toggle :dropdown
         :aria-expanded false}
        (icon :person-circle :size :medium)]
       [:ul.dropdown-menu.text-small
        [:li [:a.dropdown-item {:href "#"} "Sign In"]]]]]]]
   [:div.container
    [:h1 "Welcome!"]
    [:p.text-muted "The application is loading. Please be patient."]]])

(defn- index
  [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (str
           (h/html
             [:html {:lang "en" :data-bs-theme "dark"}
              [:head
               [:meta {:charset "utf-8"}]
               [:meta {:name "viewport"
                       :content "width=device-width, initial-scale=1"}]
               [:title "MultiMoney Double-Entry Accounting"]
               [:link {:rel "shortcut icon"
                       :href "/images/coin.svg"}]
               [:link {:rel :stylesheet
                       :href "/css/site.css"}]]
              [:body
               (mount-point)
               [:script {:type "text/javascript"
                         :src "https://unpkg.com/@popperjs/core@2"}]
               [:script {:type "text/javascript"
                         :src "/js/bootstrap.min.js"}]
               [:script {:type "text/javascript"
                         :src "/cljs-out/dev-main.js"}]]]))})

(defn- wrap-logging
  [handler]
  (fn [req]
    (log/debugf "Request %s %s" (:request-method req) (:uri req))
    (let [res (handler req)]
      (log/debugf "Response %s" (:status res))
      res)))

(defn- wrap-no-cache-header
  [handler]
  (fn [req]
    (handler (update-in req [:headers] assoc
                        "Cache-Control" "no-cache"))))

(defn- wrap-remove-last-modified-header
  [handler]
  (fn [req]
    (handler (update-in req [:headers] dissoc "Last-Modified"))))

(def app
  (ring/ring-handler
    (ring/router
      ["/" {:get {:handler index}}])
    (ring/create-default-handler)
    {:middleware [#(wrap-defaults % site-defaults)
                  wrap-logging
                  wrap-no-cache-header
                  wrap-file-etag
                  wrap-not-modified
                  wrap-remove-last-modified-header]}))
