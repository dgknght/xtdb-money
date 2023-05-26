(ns xtdb-money.models.accounts-test
  (:require [clojure.test :refer [deftest is testing]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.test-context :refer [with-context
                                             find-entity]]
            [xtdb-money.helpers :refer [with-strategy]]
            [xtdb-money.models.accounts :as acts]
            [xtdb-money.models.xtdb.ref]
            [xtdb-money.models.datomic.ref]))

(def ^:private create-ctx
  {:entities [{:name "Personal"}]})

(defn- create-an-account*
  [strategy]
  (testing strategy
    (with-strategy strategy
      (with-context create-ctx
        (let [entity (find-entity "Personal")
              account {:entity-id (:id entity)
                       :name "Checking"
                       :type :asset}]
          (acts/put account)
          (is (seq-of-maps-like? [account]
                                 (acts/select {:entity-id (:id entity)}))
              "A saved account can be retrieved"))))))

(deftest create-an-account-xtdb
  (create-an-account* "xtdb"))

(deftest create-an-account-datomic
  (create-an-account* "datomic"))

(def ^:private find-ctx
  (assoc create-ctx
         :accounts [{:entity-id "Personal"
                     :name "Salary"
                     :type :income}
                    {:entity-id "Personal"
                     :name "Checking"
                     :type :asset}]))

(defn- find-by-entity*
  [strategy]
  (testing strategy
    (with-strategy strategy
      (with-context find-ctx
        (is (= #{{:name "Salary"
                  :type :income}
                 {:name "Checking"
                  :type :asset}}
               (->> (acts/select {:entity-id (:id (find-entity "Personal"))})
                    (map #(select-keys % [:name :type]))
                    (into #{})))
            "The account with the specified name is returned")))))

(deftest find-by-entity-xtdb
  (find-by-entity* "xtdb"))

(deftest find-by-entity-datomic
  (find-by-entity* "datomic"))
