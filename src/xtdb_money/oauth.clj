(ns xtdb-money.oauth
  (:require [clj-http.client :as http]))

(defmulti fetch-profile
  (fn [provider _] provider))

(def google-userinfo-uri "https://www.googleapis.com/oauth2/v1/userinfo" )

(defmethod fetch-profile :google
  [_provider {:keys [token]}]
  (let [res (http/get google-userinfo-uri
                      {:oauth-token token
                       :accept :json
                       :as :json})]
    (if (= 200 (:status res))
      (clojure.pprint/pprint {::profile (:body res)})
      (clojure.pprint/pprint {::no-profile (:body res)}))))

(defn fetch-profiles
  "Given a map of access tokens like:
  {:google {:token \"tokenhere\"}}

  return a map of profiles like:
  {:google {:id \"profile id\"}} "
  [access-tokens]
  (->> access-tokens
       (map (fn [[provider token]]
              [provider (fetch-profile provider token)]))
       (into {})))
