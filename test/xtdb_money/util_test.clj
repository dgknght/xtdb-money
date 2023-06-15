(ns xtdb-money.util-test
  (:require [clojure.test :refer [deftest is]]
            [xtdb-money.util :as utl]))

(deftest quality-map-keys
  (is (= {:entity/name "Personal"}
         (utl/qualify-keys ^{:model-type :entity} {:name "Personal"}))
      "Unqualified keys are qualified with the model type")
  (is (= {:db/id "x"}
         (utl/qualify-keys ^{:model-type :entity} {:db/id "x"}))
      "Qualified keys are left as-is"))

(deftest add-an-id
  (is (uuid? (:id (utl/+id {})))
      "An :id attribute is added to a map that does not have an :id attribute")
  (is (= "abc" (:id (utl/+id {} (constantly "abc"))))
      "If an id fn is supplied, it is used to generate the id")
  (let [id (random-uuid)]
    (is (= {:id id}
           (utl/+id {:id id}))
        "The :id attribute is unchanged in a map that already contains one")))
