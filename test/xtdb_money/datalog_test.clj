(ns xtdb-money.datalog-test
  (:require [clojure.test :refer [deftest testing is]]
            [xtdb-money.core :as mny]
            [xtdb-money.datalog :as dtl]))

(def ^:private query '{:find [?x]})

(deftest apply-a-simple-criterion
  (is (= '{:find [?x]
           :where [[?x :entity/name ?name-in]]
           :in [?name-in]
           :args ["Personal"]}
         (dtl/apply-criteria query
                             (mny/model-type
                               {:name "Personal"}
                               :entity)))))

(deftest specify-the-args-key
  (is (= '{:find [?x]
           :where [[?x :entity/name ?name-in]]
           :in [?name-in]
           ::mny/args ["Personal"]}
         (dtl/apply-criteria query
                             (mny/model-type
                               {:name "Personal"}
                               :entity)
                             {:args-key [::mny/args]}))))

(deftest specify-the-query-key-prefix
  (is (= {:query '{:find [?x]
                   :where [[?x :entity/name ?name-in]]
                   :in [?name-in]}
          :args ["Personal"]}
         (dtl/apply-criteria {:query query}
                             (mny/model-type
                               {:name "Personal"}
                               :entity)
                             {:query-prefix [:query]}))))

(deftest apply-a-remapped-simple-criterion
  (is (= '{:find [?x]
           :where [[?x :xt/id ?id-in]]
           :in [?id-in]
           :args [123]}
         (dtl/apply-criteria query
                             (mny/model-type
                               {:id 123}
                               :entity)
                             {:remap {:id :xt/id}}))))

(deftest apply-a-comparison-criterion
  (is (= '{:find [?x]
           :where [[?x :account/balance ?balance]
                   [(>= ?balance ?balance-in)]]
           :in [?balance-in]
           :args [500M]}
         (dtl/apply-criteria query
                             (mny/model-type
                               {:balance [:>= 500M]}
                               :account)))))

(deftest apply-an-intersection-criterion
  (is (= '{:find [?x]
           :where [[?x :transaction/transaction-date ?transaction-date]
                   [(>= ?transaction-date ?transaction-date-in-1)]
                   [(< ?transaction-date ?transaction-date-in-2)]]
           :in [?transaction-date-in-1 ?transaction-date-in-2]
           :args ["2020-01-01" "2020-02-01"]}
         (dtl/apply-criteria query
                             (mny/model-type
                               {:transaction-date [:and
                                                   [:>= "2020-01-01"]
                                                   [:< "2020-02-01"]]}
                               :transaction)))
      "statements are added directly to the where chain"))

(deftest apply-options
  (testing "limit"
    (is (= '{:find [?x]
             :limit 1}
           (dtl/apply-options query
                              {:limit 1}))
        "The limit attribute is copied"))
  (testing "sorting"
    (is (= '{:find [?x]
             :order-by [[?size :asc]]}
           (dtl/apply-options query
                              {:order-by :size}))
        "A single column is symbolized and ascended is assumed")
    (is (= '{:find [?x]
             :order-by [[?size :desc]]}
           (dtl/apply-options query
                              {:order-by [[:size :desc]]}))
        "An explicit direction is copied")
    (is (= '{:find [?x]
             :order-by [[?size :asc]
                        [?weight :desc]]}
           (dtl/apply-options query
                              {:order-by [:size [:weight :desc]]}))
        "Multiple fields are handled appropriately")))
