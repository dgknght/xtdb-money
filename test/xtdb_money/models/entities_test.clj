(ns xtdb-money.models.entities-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.helpers :refer [reset-db
                                        dbs]]
            [xtdb-money.models.entities :as ents]
            [xtdb-money.models.xtdb.ref]
            [xtdb-money.models.datomic.ref]))

(use-fixtures :each reset-db)

(deftest create-an-entity
  (doseq [[name db] (dbs)]
    (testing (format "database implementation %s" name)
      (let [result (ents/put db {:name "Personal"})]
        (is (comparable? {:name "Personal"}
                         result)
            "The result contains the correct attributes")
        (is (:id result)
            "The result contains an :id value")))))
