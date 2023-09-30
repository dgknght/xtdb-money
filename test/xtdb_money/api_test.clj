(ns xtdb-money.api-test
  (:require [clojure.test :refer [deftest is]]
            [ring.mock.request :as req]
            [dgknght.app-lib.test-assertions]
            [dgknght.app-lib.test :refer [parse-json-body]]
            [xtdb-money.handler :refer [app]]))

(deftest request-an-api-resource-that-does-not-exist
  (let [res (-> (req/request :get "/api/not-a-resource")
                app
                parse-json-body)]
    (is (http-not-found? res))
    (is (= "application/json; charset=utf-8"
           (get-in res [:headers "Content-Type"]))
        "The response has JSON content type")
    (is (= {:message "not found"}
           (:json-body res))
        "The content is JSON with an error message")))
