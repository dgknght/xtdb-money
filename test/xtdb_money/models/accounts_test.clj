(ns xtdb-money.models.accounts-test
  (:require [clojure.test :refer [is use-fixtures]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.test-context :refer [with-context
                                             find-entity
                                             find-commodity
                                             find-account]]
            [xtdb-money.helpers :refer [reset-db
                                        dbtest]]
            [xtdb-money.models.accounts :as acts]
            [xtdb-money.models.mongodb.ref]
            [xtdb-money.models.sql.ref]
            [xtdb-money.models.xtdb.ref]
            [xtdb-money.models.datomic.ref]))

(use-fixtures :each reset-db)

(def ^:private create-ctx
  {:entities [{:name "Personal"}]
   :commodities [{:entity-id "Personal"
                  :type :currency
                  :name "United States Dollar"
                  :symbol "USD"}]})

(dbtest create-an-account
  (with-context create-ctx
    (let [entity (find-entity "Personal")
          commodity (find-commodity "USD")
          account {:entity-id (:id entity)
                   :name "Checking"
                   :type :asset
                   :commodity-id (:id commodity)}]
      (acts/put account)
      (is (seq-of-maps-like? [account]
                             (acts/select {:entity-id (:id entity)}))
          "A saved account can be retrieved"))))

(def ^:private find-ctx
  (assoc create-ctx
         :accounts [{:entity-id "Personal"
                     :commodity-id "USD"
                     :name "Salary"
                     :type :income}
                    {:entity-id "Personal"
                     :commodity-id "USD"
                     :name "Checking"
                     :type :asset}
                    {:entity-id "Personal"
                     :commodity-id "USD"
                     :name "Savings"
                     :type :asset}]))

(dbtest find-by-entity
  (with-context find-ctx
    (is (= #{{:name "Salary"
              :type :income}
             {:name "Checking"
              :type :asset}
             {:name "Savings"
              :type :asset}}
           (->> (acts/select {:entity-id (:id (find-entity "Personal"))})
                (map #(select-keys % [:name :type]))
                (into #{})))
        "The account with the specified name is returned")))

(dbtest create-a-child-account
        (with-context find-ctx
          (let [parent (find-account "Savings")
                attr (-> parent
                                     (select-keys [:entity-id
                                                   :type
                                                   :commodity-id])
                                     (assoc :name "Reserve"
                                            :parent-id (:id parent)))
                result (acts/put attr)]
            (is (:id result)
                "The model is returned with an :id attribute")
            (is (comparable? attr result)
                "The model is returned with the expected attributes")
            (is (comparable? attr (acts/find result))
                "The model can be retrieved"))))
