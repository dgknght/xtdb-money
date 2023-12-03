(ns xtdb-money.auth-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.pprint :refer [pprint]]
            [clj-http.client :as http]
            [ring.middleware.session :refer [session-request]]
            [ring.mock.request :as req]
            [lambdaisland.uri :refer [uri
                                      map->query-string
                                      query-string->map]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.tokens :as tkns]
            [xtdb-money.test-context :refer [with-context]]
            [xtdb-money.helpers :refer [reset-db]]
            [xtdb-money.handler :refer [app]]))

(use-fixtures :each reset-db)

(def ^:private no-profile-ctx
  {:users [{:email "john@doe.com"
            :given-name "John"
            :surname "Doe"}]})

(def ^:private oauth-id "1001")
(def ^:private existing-profile-ctx
  (assoc-in no-profile-ctx
            [:users 0 :identities :google]
            oauth-id))

(deftest initiate-google-oauth
  (let [{:keys [headers status]} (app (req/request :get "/oauth/google"))
        location (uri (get-in headers ["Location"]))
        query (-> location
                  :query
                  query-string->map)]
    (is (= 302 status)
        "The response is a redirect")
    (is (= "https"
           (:scheme location))
        "The redirect schema is https")
    (is (= "accounts.google.com"
           (:host location))
        "The redirect host is accounts.google.com")
    (is (= "/o/oauth2/v2/auth"
           (:path location))
        "The redirect path is correct")
    (is (comparable? {:response_type "code"
                      :client_id "google-client-id"
                      :redirect_uri "http://localhost/oauth/google/callback"
                      :scope "email profile"}
                     query)
        "The query string contains the correct values")
    (is (:state query)
        "The query string contains a state field")))

(deftest receive-google-oauth-redirect
  (let [query {:state "RiTCVbqy57SN"
               :code "abc123"
               :scope  "email profile https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile openid"
               :authuser "0"
               :prompt "consent"}
        url (-> "/oauth/google/callback"
                uri
                (assoc :query (map->query-string query))
                str)
        res (with-redefs
              [session-request (fn [req & _]
                                 (assoc req
                                        :session {:ring.middleware.oauth2/state "RiTCVbqy57SN"}
                                        :session/key :query-params))
               http/post (fn [url & _]
                           (if (= url "https://www.googleapis.com/oauth2/v4/token")
                             {:status 200
                              :body {:access_token "abc123"
                                     :expires_in 60
                                     :refresh_token "123abc"
                                     :id_token "a1b2c3"}}
                             {:status 404
                              :body {:message "No mock found"}}))]
              (app (req/request :get url)))]
    (is (http-redirect-to? "/" res)
        "The user is redirected to the root")))

(deftest lookup-existing-user-from-oauth-profile
  (with-context existing-profile-ctx
    (let [res (with-redefs
                [session-request
                 (fn [req & _]
                   (assoc req
                          :session {:ring.middleware.oauth2/access-tokens
                                    {:google {:token "abc123"
                                              :extra-data {:scope "https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email openid"
                                                           :token_type "Bearer"}
                                              :expires #inst "9999-12-31T00:00:00Z"
                                              :id-token "def456"}}}))
                 http/get (fn [url & _]
                            (if (= url "https://www.googleapis.com/oauth2/v1/userinfo")
                              {:status 200
                               :body {:id oauth-id
                                      :email "john@doe.com"
                                      :given_name "John"
                                      :family_name "Doe"}}
                              {:status 404
                               :body {:message "No mock found"}}))
                 tkns/encode (fn [_] "encoded-token")]
                (app (req/request :get "/")))]
      (is (http-success? res))
      (is (http-response-with-cookie? "auth-token" "encoded-token" res)))))
