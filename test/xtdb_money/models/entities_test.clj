(ns xtdb-money.models.entities-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.core :refer [with-storage]]
            [xtdb-money.helpers :refer [reset-db
                                        dbs]]
            [xtdb-money.models.entities :as ents]
            [xtdb-money.models.xtdb.ref]
            [xtdb-money.datomic]))

(use-fixtures :each reset-db)

(deftest create-an-entity
  (doseq [[name config] (dbs)]
    (testing (format "database implementation %s" name)
      (with-storage [config]
        (let [result (ents/put {:name "Personal"})]
          (is (comparable? {:name "Personal"}
                           result)
              "The result contains the correct attributes")
          (is (:id result)
              "The result contains an :id value"))))))
