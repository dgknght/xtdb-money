(ns xtdb-money.models.entities-test
  (:require [clojure.test :refer [deftest testing is]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.helpers :refer [with-strategy]]
            [xtdb-money.models.entities :as ents]
            [xtdb-money.models.xtdb.ref]
            [xtdb-money.models.datomic.ref]))

(defn- create-an-entity*
  [strategy]
  (testing strategy
    (with-strategy strategy
      (let [result (ents/put {:name "Personal"})]
        (is (comparable? {:name "Personal"}
                         result)
            "The result contains the correct attributes")
        (is (:id result)
            "The result contains an :id value"))
      (is (seq-of-maps-like? [{:name "Personal"}]
                             (ents/select))))))

(deftest create-an-entity-xtdb
  (create-an-entity* "xtdb"))

(deftest create-an-entity-datomic
  (create-an-entity* "datomic"))
