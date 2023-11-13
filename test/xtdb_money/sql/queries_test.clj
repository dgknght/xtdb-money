(ns xtdb-money.sql.queries-test
  (:require [clojure.test :refer [deftest is]]
            [clj-time.core :as t]
            [xtdb-money.core :as mny]
            [xtdb-money.sql.queries :as qry]))

(deftest convert-empty-criteria
  (is (= ["SELECT * FROM users"]
         (qry/criteria->query (mny/model-type {} :user)))))

(deftest convert-a-simple-criterion
  (is (= ["SELECT * FROM users WHERE given_name = ?" "John"]
         (-> {:given-name "John"}
             (mny/model-type :user)
             qry/criteria->query))))

(deftest convert-a-simple-criterion-with-convertible-value
  (is (= ["SELECT * FROM users WHERE birthday = ?" (java.sql.Date. 946684800000)]
         (-> {:birthday (t/local-date 2000 1 1)}
             (mny/model-type :user)
             qry/criteria->query))))

(deftest convert-compound-criteria
  (is (= ["SELECT * FROM users WHERE (given_name = ?) AND (surname = ?)" "John" "Doe"]
         (-> {:given-name "John"
              :surname "Doe"}
             (mny/model-type :user)
             qry/criteria->query))))

(deftest convert-union-criteria
  (is (= ["SELECT * FROM users WHERE (given_name = ?) OR (surname = ?)" "John" "Doe"]
         (-> [:or
              {:given-name "John"}
              {:surname "Doe"}]
             (mny/model-type :user)
             qry/criteria->query))))

(deftest convert-comparision-operator
  (is (= ["SELECT * FROM users WHERE age > ?" 21]
         (-> {:age [:> 21]}
             (mny/model-type :user)
             qry/criteria->query))))
