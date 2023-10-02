(ns xtdb-money.middleware
  (:require [clojure.tools.logging :as log]
            [config.core :refer [env]]
            [ring.middleware.oauth2 :refer [wrap-oauth2]]
            [ring.util.response :as res]
            [xtdb-money.core :as mny]))

(defn wrap-logging
  [handler]
  (fn [req]
    (log/infof "Request %s %s" (:request-method req) (:uri req))
    (log/tracef "State %s" (pr-str (:state req)))
    (let [res (handler req)]
      (log/infof "Response %s %s -> %s"
                 (:request-method req)
                 (:uri req)
                 (:status res))
      (log/tracef "Response %s %s -> %s %s %s"
                  (:request-method req)
                  (:uri req)
                  (:status res)
                  (pr-str (:headers res))
                  (:body res))

      res)))

(defn wrap-no-cache-header
  [handler]
  (fn [req]
    (handler (update-in req [:headers] assoc
                        "Cache-Control" "no-cache"))))

(defn wrap-remove-last-modified-header
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

(defn wrap-db
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

(def wrap-oauth
  #(wrap-oauth2
     %
     {:google
      {:authorize-uri "https://accounts.google.com/o/oauth2/v2/auth"
       :access-token-uri "https://www.googleapis.com/oauth2/v4/token"
       :client-id (env :google-oauth-client-id)
       :client-secret (env :google-oauth-client-secret)
       :scopes ["email" "profile"]
       :launch-uri "/oauth/google"
       :redirect-uri "/oauth/google/callback"
       :landing-uri "/"}}))
