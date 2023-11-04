(ns xtdb-money.handler
  (:require [clojure.pprint :refer [pprint]]
            [hiccup.page :as page]
            [reitit.core :as r]
            [reitit.ring :as ring]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.json :refer [wrap-json-body
                                          wrap-json-response]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.util.response :as res]
            [co.deps.ring-etag-middleware :refer [wrap-file-etag]]
            [xtdb-money.middleware :refer [wrap-no-cache-header
                                           wrap-api-exception
                                           wrap-remove-last-modified-header
                                           wrap-db
                                           wrap-logging
                                           wrap-oauth
                                           wrap-fetch-oauth-profile
                                           wrap-user-lookup
                                           wrap-issue-auth-token
                                           wrap-site
                                           wrap-authentication]]
            [xtdb-money.models.mongodb.ref]
            [xtdb-money.models.sql.ref]
            [xtdb-money.models.xtdb.ref]
            [xtdb-money.models.datomic.ref]
            [xtdb-money.api.entities :as ents]
            [xtdb-money.icons :refer [icon]]))

(defn- mount-point []
  [:div#app
   [:header.p-3.mb-3.border-bottom
    [:div.container
     [:div.d-flex.flex-wrap.align-items-center.justify-content-center.justify-content-lg-start
      [:a.d-flex.align-items-center.mb-2.mb-lg-0.link-body-emphasis.text-decoration-none.me-2
       {:href "/"}
       (icon :cash-coin :size :large)]
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
           (page/html5
             {:data-bs-theme "dark"
              :lang "en"}
             [:head
              [:meta {:charset "utf-8"}]
              [:meta {:name "viewport"
                      :content "width=device-width, initial-scale=1"}]
              [:title "MultiMoney Double-Entry Accounting"]
              [:link {:rel "shortcut icon"
                      :href "/assets/images/coin.svg"}]
              [:link {:rel :stylesheet
                      :href "/assets/css/site.css"}]]
             [:body
              (mount-point)
              [:script {:type "text/javascript"
                        :src "https://unpkg.com/@popperjs/core@2"}]
              [:script {:type "text/javascript"
                        :src "/assets/js/bootstrap.min.js"}]
              [:script {:type "text/javascript"
                        :src "/assets/cljs-out/dev-main.js"}]]))})

(defn- not-found
  [{:keys [uri]}]
  (if (re-find #"^/api" uri)
    {:status 404 :body {:message "not found"}}
    (res/redirect "/#not-found")))

(def app
  (ring/ring-handler
    (ring/router
      [["/" {:middleware [(wrap-site)
                          wrap-content-type
                          wrap-no-cache-header
                          wrap-file-etag
                          wrap-not-modified
                          wrap-remove-last-modified-header
                          wrap-logging
                          wrap-oauth
                          wrap-fetch-oauth-profile
                          wrap-user-lookup
                          wrap-issue-auth-token]}
        ["" {:get {:handler index}}]
        ["oauth/*" {:get (wrap-json-response not-found)}]]
       ["/api" {:middleware [[wrap-defaults api-defaults]
                             [wrap-json-body {:keywords? true :bigdecimals? true}]
                             wrap-json-response
                             wrap-api-exception
                             wrap-db
                             wrap-logging
                             wrap-authentication
                             wrap-no-cache-header
                             wrap-file-etag
                             wrap-not-modified
                             wrap-remove-last-modified-header]}
        ents/routes]
       ["/assets/*" (ring/create-resource-handler)]])
    (ring/create-default-handler {:not-found (wrap-json-response not-found)})))

(defn print-routes []
  (pprint
    (map (comp #(take 2 %)
               #(update-in % [1] dissoc :middleware))
         (-> app
             ring/get-router
             r/compiled-routes))))
