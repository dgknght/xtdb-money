(ns xtdb-money.middleware
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [config.core :refer [env]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults ]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.middleware.oauth2 :refer [wrap-oauth2]]
            [ring.util.response :as res]
            [dgknght.app-lib.api :as api]
            [xtdb-money.core :as mny]
            [xtdb-money.tokens :as tkns]
            [xtdb-money.models.users :as usrs]
            [xtdb-money.oauth :refer [fetch-profiles]])
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

(defn wrap-fetch-oauth-profile
  [handler]
  (fn [{:oauth2/keys [access-tokens] :as req}]
    (handler (if-let [profiles (seq (fetch-profiles access-tokens))]
               (assoc req :oauth2/profiles profiles)
               req))))

(defn wrap-user-lookup
  [handler]
  (fn [{:oauth2/keys [profiles] :as req}]
    (handler (if-let [user (when (seq profiles)
                             (some usrs/find-by-oauth
                                   profiles))]
               (assoc req :authenticated user)
               req))))

(defn wrap-issue-auth-token
  [handler]
  (fn [{:keys [authenticated] :as req}]
    (handler (cond-> req
               authenticated (res/set-cookie :auth-token
                                             (tkns/encode (usrs/tokenize authenticated))
                                             {:same-site true
                                              :max-age (* 6 60 60)})))))

(defn- extract-authorization
  [{:keys [headers]}]
  (when-let [authorization (headers "authorization")]
    (when-let [parsed (re-find #"(?<=^Bearer ).*" authorization)]
      (usrs/detokenize (tkns/decode parsed)))))

; Validates the credentials of the request by token bearer
; and associates the found user with the request
(defn wrap-authenticate
  [handler]
  (fn [req]
    (if-let [user (usrs/detokenize (extract-authorization req))]
      (handler (assoc req :authenticated user))
      api/unauthorized)))

(defn wrap-site []
  (let [c-store (cookie-store)]
    [wrap-defaults (update-in site-defaults
                              [:session]
                              merge
                              {:store c-store
                               :cookie-attrs {:same-site :lax
                                              :http-only true}})]))

(def wrap-oauth
  [wrap-oauth2
   {:google
    {:authorize-uri "https://accounts.google.com/o/oauth2/v2/auth"
     :access-token-uri "https://www.googleapis.com/oauth2/v4/token"
     :client-id (env :google-oauth-client-id)
     :client-secret (env :google-oauth-client-secret)
     :scopes ["email" "profile"]
     :launch-uri "/oauth/google"
     :redirect-uri "/oauth/google/callback"
     :landing-uri "/"}}])
