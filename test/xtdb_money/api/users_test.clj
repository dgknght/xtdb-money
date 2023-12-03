(ns xtdb-money.api.users-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [dgknght.app-lib.web :refer [path]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.test-context :refer [with-context
                                             find-user]]
            [xtdb-money.helpers :refer [reset-db
                                        request]]))

(use-fixtures :each reset-db)

(def ^:private user-ctx
  {:users [{:email "john@doe.com"
            :given-name "John"
            :surname "Doe"}]})

(deftest get-my-profiles
  (with-context user-ctx
    (let [user (find-user "john@doe.com")
          res (request :get (path :api :me)
                       :user user)]
      (is (http-success? res))
      (is (comparable? {:email "john@doe.com"
                        :given-name "John"
                        :surname "Doe"}
                       (:json-body res))
          "The body contains the user profile"))))
