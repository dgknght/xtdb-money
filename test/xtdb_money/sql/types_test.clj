(ns xtdb-money.sql.types-test
  (:require [clojure.test :refer [deftest is]]
            [clj-time.core :as t]
            [xtdb-money.sql.types :as typ]))

(deftest coerce-an-id
  (is (= 1 (typ/coerce-id "1"))
      "A string is converted to Long")
  (is (= 1 (typ/coerce-id 1))
      "A Long is returned as-is"))

(deftest convert-a-date-to-storable
  (let [date (t/local-date 2000 1 1)
        converted (typ/->storable date)]
    (is (= java.sql.Date (type converted))
        "The return type is a SQL date")
    (is (= date (typ/<-storable converted))
        "The converted value is restored to the correct date value")))

(deftest noop-on-non-converting-values
  (is (= "test" (typ/->storable "test"))
      "A String is returned as-is"))
