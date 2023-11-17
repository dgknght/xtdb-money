(ns xtdb-money.mongodb.types-test
  (:require [clojure.test :refer [deftest is]]
            [xtdb-money.mongodb.types :as typs])
  (:import org.bson.types.ObjectId))

(def id-string "6556abcc162a2c15f188a9ad")
(def id (ObjectId. id-string))

(deftest coerce-an-id-to-the-correct-type
  (is (= id (typs/coerce-id id-string))
      "A string is converted to ObjectId")
  (is (= id (typs/coerce-id id-string))
      "A BSON ObjectId is returned as-is"))

(deftest safely-coerce-an-id-that-might-be-nil
  (is (= id (typs/safe-coerce-id id-string))
      "A string is converted to ObjectId")
  (is (= id (typs/safe-coerce-id id-string))
      "A BSON ObjectId is returned as-is")
  (is (nil? (typs/safe-coerce-id nil))
      "Nil is returned as-is"))
