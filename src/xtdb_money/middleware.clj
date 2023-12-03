(ns xtdb-money.middleware
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [clojure.pprint :refer [pprint]]
            [config.core :refer [env]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults ]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.middleware.oauth2 :refer [wrap-oauth2]]
            [ring.util.response :as res]
            [dgknght.app-lib.api :as api]
            [dgknght.app-lib.authorization :as auth]
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
        (case (:type (ex-data e))
          ::auth/not-found api/not-found
          ::auth/forbidden api/forbidden
          error-res))
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

(defn- extract-db-strategy
  [req]
  (or (some #(:value (get-in req %))
            [[:headers "db-strategy"]
             [:cookies "db-strategy"]])
      (get-in env [:db :active])))

(defn wrap-db
  [handler]
  (fn [req]
    (let [storage-key (extract-db-strategy req)
          storage-config (get-in env [:db :strategies storage-key])]
      (log/debugf "Handling request with db strategy %s -> %s"
                  storage-key
                  (mask-values storage-config [:username :user :password]))
      (mny/with-db [storage-config]
        (handler (assoc req :db-strategy storage-key))))))

(defn wrap-fetch-oauth-profile
  [handler]
  (fn [{:oauth2/keys [access-tokens] :as req}]
    (handler (if-let [profiles (seq (fetch-profiles access-tokens))]
               (assoc req :oauth2/profiles profiles)
               req))))

(defn- find-or-create-user
  [profiles]
  (when (seq profiles)
    (try (or (some usrs/find-by-oauth profiles)
             (some usrs/create-from-oauth profiles))
         (catch Exception e
           (log/error e "Unable to fetch the user from the oauth profile")))))

(defn wrap-user-lookup
  [handler]
  (fn [{:oauth2/keys [profiles] :as req}]
    (handler (if-let [user (find-or-create-user profiles)]
               (assoc req :authenticated user)
               req))))

(defn wrap-issue-auth-token
  [handler]
  (fn [{:keys [authenticated] :as req}]
    (let [cookie-val (when authenticated
                       (-> (usrs/tokenize authenticated)
                           (assoc :user-agent
                                  (get-in req
                                          [:headers "user-agent"]))
                           tkns/encode))
          res (handler req)]
      (cond-> res
        cookie-val (res/set-cookie
                     "auth-token"
                     cookie-val
                     {:same-site :strict
                      :max-age (* 6 60 60)})))))

(defn- validate-token-and-lookup-user
  [req]
  (let [decoded (-> req
                    api/extract-token-bearer
                    tkns/decode)]
    (when (= (get-in req [:headers "user-agent"])
             (:user-agent decoded))
      (usrs/detokenize decoded))))

; Validates the credentials of the request by token bearer
; and associates the found user with the request
(def wrap-authentication
  [api/wrap-authentication
   {:authenticate-fn validate-token-and-lookup-user}])

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
