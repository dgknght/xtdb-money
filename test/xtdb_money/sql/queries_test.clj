(ns xtdb-money.sql.queries-test
  (:require [clojure.test :refer [deftest is]]
            [xtdb-money.core :as mny]
            [xtdb-money.sql.queries :as qry]))

(deftest apply-a-limit-to-a-query
  (is (= ["SELECT * FROM users LIMIT ?" 1]
         (qry/criteria->query (mny/model-type {} :user)
                              {:limit 1}))))

(deftest apply-a-sort-to-a-query
  (is (= ["SELECT * FROM users ORDER BY last_name DESC, first_name ASC"]
         (qry/criteria->query (mny/model-type {} :user)
                              {:order-by [[:last-name :desc] :first-name]}))))
