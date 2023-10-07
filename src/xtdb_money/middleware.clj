(ns xtdb-money.middleware
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as string]
            [config.core :refer [env]]
            [ring.middleware.oauth2 :refer [wrap-oauth2]]
            [ring.util.response :as res]
            [xtdb-money.core :as mny]
            [xtdb-money.oauth :refer [fetch-profile]])
  (:import clojure.lang.ExceptionInfo))

(def error-res
  {:status 500
   :body {:message "server error"}})

(defn wrap-api-exception
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

(defn wrap-logging
  [handler]
  (fn [req]
    (log/infof "Request %s %s" (:request-method req) (:uri req))
    (log/tracef "Request details\n  query-params %s\n  cookies %s\n  session %s"
                (pr-str (:query-params req))
                (pr-str (:cookies req))
                (pr-str (:session req)))
    (let [res (handler req)]
      (log/infof "Response %s %s -> %s"
                 (:request-method req)
                 (:uri req)
                 (:status res))
      (log/tracef "Response details\n  cookies %s\n  session %s\n  headers %s\n  body %s"
                  (:cookies res)
                  (:session res)
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

(defn wrap-auth-resolution
  [handler]
  (fn [{:oauth2/keys [access-tokens] :as req}]
    ; TODO: Why is the session being cleared on the redirect from /oauth/google/callback to /?
    (clojure.pprint/pprint {::wrap-auth-resolution access-tokens})
    (handler req)))

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
