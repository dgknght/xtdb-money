(ns xtdb-money.models.entities-test
  (:require [clojure.test :refer [is use-fixtures]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.helpers :refer [reset-db
                                        dbtest]]
            [xtdb-money.test-context :refer [with-context
                                             find-entity
                                             find-commodity]]
            [xtdb-money.models.entities :as ents]
            [xtdb-money.models.mongodb.ref]
            [xtdb-money.models.sql.ref]
            [xtdb-money.models.xtdb.ref]
            [xtdb-money.models.datomic.ref]))

(use-fixtures :each reset-db)

(dbtest create-an-entity
  (let [result (ents/put {:name "Personal"})]
    (is (comparable? {:name "Personal"}
                     result)
        "The result contains the correct attributes")
    (is (:id result)
        "The result contains an :id value")))

(def ^:private update-context
  {:entities [{:name "Personal"}]
   :commodities [{:name "United States Dollar"
                  :symbol "USD"
                  :type :currency
                  :entity-id "Personal"}]})

(dbtest update-an-entity
        (with-context update-context
          (let [entity (find-entity "Personal")
                commodity (find-commodity "USD")
                updated (ents/put (assoc entity :default-commodity-id (:id commodity)))]
            (is (= (:id commodity)
                   (:default-commodity-id updated))
                "The result contains the updated attributes")
            (is (= (:id commodity)
                   (:default-commodity-id (ents/find entity)))
                "A retrieved model has the updated attributes"))))
