(ns xtdb-money.models.entities-test
  (:require [clojure.test :refer [is use-fixtures]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.helpers :refer [reset-db
                                        dbtest]]
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
