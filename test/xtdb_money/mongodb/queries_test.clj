(ns xtdb-money.mongodb.queries-test
    (:require [clojure.test :refer [deftest is]]
              [xtdb-money.mongodb.queries :as qrs]))

(deftest create-a-simple-query
    (is (= {:where {:name "John"}}
           (qrs/apply-criteria {} {:name "John"}))))

(deftest apply-a-comparison-criterion
    (is false "need to write the test"))

(deftest apply-an-elem-match-criterion
    (is false "need to write the test"))

(deftest apply-a-sort
    (is false "need to write the test"))

(deftest apply-a-limit
    (is false "need to write the test"))

(deftest apply-an-offset
    (is false "need to write the test"))
