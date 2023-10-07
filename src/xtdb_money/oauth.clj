(ns xtdb-money.oauth
  (:require [clj-http.client :as http]))

(defmulti fetch-profile
  (fn [provider _token] provider))

(def google-userinfo-uri "https://www.googleapis.com/oauth2/v1/userinfo" )

(defmethod fetch-profile :google
  [_provider token]
  (let [res (http/get google-userinfo-uri
                      {:oauth-token token})]
    (if (= 200 (:status res))
      (clojure.pprint/pprint {::profile (:body res)})
      (clojure.pprint/pprint {::no-profile (:body res)}))))
