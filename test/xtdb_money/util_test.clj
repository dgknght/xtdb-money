(ns xtdb-money.util-test
  (:require [clojure.test :refer [deftest is]]
            [xtdb-money.util :as utl]))

(deftest quality-map-keys
  (is (= {:entity/name "Personal"}
         (utl/qualify-keys ^{:model-type :entity} {:name "Personal"}))))

(deftest add-an-id
  (is (uuid? (:id (utl/+id {})))
      "An :id attribute is added to a map that does not have an :id attribute")
  (let [id (random-uuid)]
    (is (= {:id id}
           (utl/+id {:id id}))
        "The :id attribute is unchanged in a map that already contains one")))
