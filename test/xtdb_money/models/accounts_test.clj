(ns xtdb-money.models.accounts-test
  (:require [clojure.test :refer [is use-fixtures]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.test-context :refer [with-context
                                             find-entity]]
            [xtdb-money.helpers :refer [reset-db
                                        dbtest]]
            [xtdb-money.models.accounts :as acts]
            [xtdb-money.models.xtdb.ref]
            [xtdb-money.models.datomic.ref]))

(use-fixtures :each reset-db)

(def ^:private create-ctx
  {:entities [{:name "Personal"}]})

(dbtest create-an-account
  (with-context create-ctx
    (let [entity (find-entity "Personal")
          account {:entity-id (:id entity)
                   :name "Checking"
                   :type :asset}]
      (acts/put account)
      (is (seq-of-maps-like? [account]
                             (acts/select {:entity-id (:id entity)}))
          "A saved account can be retrieved"))))

(def ^:private find-ctx
  (assoc create-ctx
         :accounts [{:entity-id "Personal"
                     :name "Salary"
                     :type :income}
                    {:entity-id "Personal"
                     :name "Checking"
                     :type :asset}]))

(dbtest find-by-entity
  (with-context find-ctx
    (is (= #{{:name "Salary"
              :type :income}
             {:name "Checking"
              :type :asset}}
           (->> (acts/select {:entity-id (:id (find-entity "Personal"))})
                (map #(select-keys % [:name :type]))
                (into #{})))
        "The account with the specified name is returned")))
