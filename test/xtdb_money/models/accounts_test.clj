(ns xtdb-money.models.accounts-test
  (:require [clojure.test :refer [is use-fixtures testing]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.test-context :refer [with-context
                                             find-entity
                                             find-commodity
                                             find-account]]
            [xtdb-money.helpers :refer [reset-db
                                        dbtest]]
            [xtdb-money.accounts :refer [nest unnest]]
            [xtdb-money.models.accounts :as acts]
            [xtdb-money.models.mongodb.ref]
            [xtdb-money.models.sql.ref]
            [xtdb-money.models.xtdb.ref]
            [xtdb-money.models.datomic.ref]))

(use-fixtures :each reset-db)

(def ^:private create-ctx
  {:users [{:email "john@doe.com"
            :given-name "John"
            :surname "Doe"}]
   :entities [{:user-id "john@doe.com"
               :name "Personal"}]
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


(def ^:private nested-ctx
  (update-in find-ctx [:accounts] concat [{:name "Reserve"
                                           :type :asset
                                           :parent-id "Savings"
                                           :entity-id "Personal"
                                           :commodity-id "USD"}
                                          {:name "Car"
                                           :type :asset
                                           :parent-id "Savings"
                                           :entity-id "Personal"
                                           :commodity-id "USD"}]))
(defn- simplify-account
  [account]
  (-> account
      (select-keys [:name :depth :children :path])
      (update-in [:children] #(map simplify-account %))))

(dbtest get-nested-accounts
   (with-context nested-ctx
     (let [entity (find-entity "Personal")
           nested (nest (acts/select {:entity-id (:id entity)}))]
       (testing "nest accounts"
         (is (= [{:name "Checking"
                  :path ["Checking"]
                  :depth 0
                  :children []}
                 {:name "Salary"
                  :path ["Salary"]
                  :depth 0
                  :children []}
                 {:name "Savings"
                  :path ["Savings"]
                  :depth 0
                  :children [{:name "Car"
                              :path ["Savings" "Car"]
                              :depth 1
                              :children []}
                             {:name "Reserve"
                              :path ["Savings" "Reserve"]
                              :depth 1
                              :children []}]}]
                (map simplify-account
                     nested))))
       (testing "unnest accounts"
         (is (seq-of-maps-like? [{:name "Checking"
                                  :depth 0
                                  :path ["Checking"]}
                                 {:name "Salary"
                                  :depth 0
                                  :path ["Salary"]}
                                 {:name "Savings"
                                  :depth 0
                                  :path ["Savings"]}
                                 {:name "Car"
                                  :depth 1
                                  :path ["Savings" "Car"]}
                                 {:name "Reserve"
                                  :depth 1
                                  :path ["Savings" "Reserve"]}]
                                (unnest nested)))))))
