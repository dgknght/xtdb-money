(ns xtdb-money.models.accounts-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.test-context :refer [with-context
                                             find-entity]]
            [xtdb-money.helpers :refer [reset-db]]
            [xtdb-money.models.accounts :as acts]))

(use-fixtures :each reset-db)

(def ^:private account-ctx
  {:entities [{:name "Personal"}]})

(deftest create-an-account
  (with-context account-ctx
    (let [entity (find-entity "Personal")
          account {:entity-id (:id entity)
                   :name "Checking"
                   :type :asset}]
      (acts/put account)
      (is (seq-of-maps-like? [account]
                             (acts/select))
          "A saved account can be retrieved"))))

; TODO: Change this select-by-entity-id
(deftest find-an-account-by-name
  (with-context
    (is (comparable? {:name "Salary"
                      :type :income}
                     (acts/find-by-name "Salary"))
        "The account with the specified name is returned")))
