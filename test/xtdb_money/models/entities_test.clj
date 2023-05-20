(ns xtdb-money.models.entities-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.helpers :refer [reset-db]]
            [xtdb-money.models.entities :as ents]
            [xtdb-money.models.xtdb.ref]))

(use-fixtures :each reset-db)

(deftest create-an-entity
  (testing "xtdb"
    (ents/put {:name "Personal"})
    (is (seq-of-maps-like? [{:name "Personal"}]
                           (ents/select)))))
