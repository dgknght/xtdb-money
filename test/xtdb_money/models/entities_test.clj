(ns xtdb-money.models.entities-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [xtdb-money.helpers :refer [reset-db]]
            [xtdb-money.models.entities :as ents]))

(use-fixtures :each reset-db)

(deftest create-an-entity
  (ents/put {:name "Personal"})
  (is (seq-of-maps-like? [{:name "Personal"}]
                         (ents/select))))
