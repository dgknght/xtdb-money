(ns xtdb-money.handler
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as string]
            [config.core :refer [env]]
            [hiccup.page :as page]
            [reitit.core :as r]
            [reitit.ring :as ring]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults api-defaults]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.json :refer [wrap-json-body
                                          wrap-json-response]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.util.response :as res]
            [co.deps.ring-etag-middleware :refer [wrap-file-etag]]
            [xtdb-money.models.mongodb.ref]
            [xtdb-money.models.sql.ref]
            [xtdb-money.models.xtdb.ref]
            [xtdb-money.models.datomic.ref]
            [xtdb-money.core :as mny]
            [xtdb-money.api.entities :as ents]
            [xtdb-money.icons :refer [icon]])
  (:import clojure.lang.ExceptionInfo))

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

(defn- wrap-logging
  [handler]
  (fn [req]
    (log/debugf "Request %s %s" (:request-method req) (:uri req))
    (let [res (handler req)]
      (log/debugf "Response %s %s -> %s (%s) "
                  (:request-method req)
                  (:uri req)
                  (:status res)
                  (get-in res [:headers "Content-Type"]))
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

(defn- mask-values
  [m ks]
  (reduce (fn [res k]
            (if (contains? res k)
              (assoc res k "****************")
              res))
          m
          ks))

(defn- wrap-db
  [handler]
  (fn [req]
    (if-let [storage-key (get-in req
                                 [:headers "db-strategy"]
                                 (get-in env [:db :active]))]
      (let [storage-config (get-in env [:db :strategies storage-key])]
        (log/debugf "Handling request with db strategy %s: %s"
                    storage-key
                    (mask-values storage-config [:username :user :password]))
        (mny/with-db [storage-config]
          (handler (assoc req :db-strategy storage-key))))
      (-> (res/response {:message "bad request: must specify a db-strategy header"})
          (res/status 400)))))

(def error-res
  {:status 500
   :body {:message "server error"}})

(defn- wrap-api-exception
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch ExceptionInfo e
        (log/errorf "Unexpected error while handling API request: %s - %s"
                    (.getMessage e)
                    (pr-str (ex-data e)))
        error-res)
      (catch Exception e
        (log/errorf "Unexpected error while handling API request: %s - %s"
                    (.getMessage e)
                    (->> (.getStackTrace e)
                         (map #(format "%s.%s at %s:%s"
                                       (.getClassName %)
                                       (.getMethodName %)
                                       (.getFileName %)
                                       (.getLineNumber %)))
                         (string/join "\n  ")))
        error-res))))

(defn- not-found
  [{:keys [uri] :as req}]
  (if (re-find #"^/api" uri)
    {:status 404 :body {:message "not found"}}
    (res/redirect "/#not-found")))

(def app
  (ring/ring-handler
    (ring/router
      [["/" {:get {:handler index}
             :middleware [#(wrap-defaults % (dissoc site-defaults :static :session))]}]
       ["/api" {:middleware [#(wrap-defaults % api-defaults)
                             #(wrap-json-body % {:keywords? true :bigdecimals? true})
                             wrap-json-response
                             wrap-api-exception
                             wrap-db]}
        ents/routes]
       ["/assets/*" (ring/create-resource-handler)]])
    (ring/create-default-handler {:not-found (wrap-json-response not-found)})
    {:middleware [wrap-logging
                  wrap-content-type
                  wrap-no-cache-header
                  wrap-file-etag
                  wrap-not-modified
                  wrap-remove-last-modified-header]}))

(defn print-routes []
  (pprint
    (map (comp #(take 2 %)
               #(update-in % [1] dissoc :middleware))
         (-> app
             ring/get-router
             r/compiled-routes))))
