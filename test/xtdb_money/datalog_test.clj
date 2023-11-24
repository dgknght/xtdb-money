(ns xtdb-money.datalog-test
  (:require [clojure.test :refer [deftest testing is testing]]
            [xtdb-money.core :as mny]
            [xtdb-money.datalog :as dtl]))

(def ^:private query '{:find [?x]})

(deftest apply-a-simple-criterion
  (is (= '{:find [?x]
           :where [[?x :entity/name ?name-in]]
           :in [?name-in]
           :args ["Personal"]}
         (dtl/apply-criteria query
                             {:name "Personal"}
                             {:model-type :entity}))))

(deftest specify-the-args-key
  (is (= '{:find [?x]
           :where [[?x :entity/name ?name-in]]
           :in [?name-in]
           ::mny/args ["Personal"]}
         (dtl/apply-criteria query
                             {:name "Personal"}
                             {:args-key [::mny/args]
                              :model-type :entity}))))

(deftest specify-the-query-key-prefix
  (is (= {:query '{:find [?x]
                   :where [[?x :entity/name ?name-in]]
                   :in [?name-in]}
          :args ["Personal"]}
         (dtl/apply-criteria {:query query}
                             {:name "Personal"}
                             {:query-prefix [:query]
                              :model-type :entity}))))

(deftest apply-a-remapped-simple-criterion
  (is (= '{:find [?x]
           :where [[?x :xt/id ?id-in]]
           :in [?id-in]
           :args [123]}
         (dtl/apply-criteria query
                             {:id 123}
                             {:remap {:id :xt/id}
                              :model-type :entity}))))

(deftest apply-a-comparison-criterion
  (is (= '{:find [?x]
           :where [[?x :account/balance ?balance]
                   [(>= ?balance ?balance-in)]]
           :in [?balance-in]
           :args [500M]}
         (dtl/apply-criteria query
                             {:balance [:>= 500M]}
                             {:model-type :account}))))

(deftest apply-an-intersection-criterion
  (is (= '{:find [?x]
           :where [[?x :transaction/transaction-date ?transaction-date]
                   [(>= ?transaction-date ?transaction-date-in-1)]
                   [(< ?transaction-date ?transaction-date-in-2)]]
           :in [?transaction-date-in-1 ?transaction-date-in-2]
           :args ["2020-01-01" "2020-02-01"]}
         (dtl/apply-criteria query
                             {:transaction-date [:and
                                                 [:>= "2020-01-01"]
                                                 [:< "2020-02-01"]]}
                             {:model-type :transaction}))
      "statements are added directly to the where chain"))

(deftest apply-a-tuple-match-criterion
  (is (= '{:find [?x]
           :where [[?x :user/identities ?identities-in]]
           :in [?identities-in]
           :args [[:google "abc123"]]}
         (dtl/apply-criteria query
                             {:identities [:= [:google "abc123"]]}
                             {:model-type :user}))
      "Using :match, a vector is passed in as the match value"))

(deftest apply-a-union-of-criterias
  ; Note that the order of the criteria reverses as a result of
  ; the implementation. Trying to write a test that allows for
  ; the criteria in the result to be in any order that produces
  ; the same logical result makes my head hurt
  ; makes my head hurt
  (is (= '{:find [?x]
           :where (or [?x :transaction/credit-account-id ?credit-account-id-in]
                      [?x :transaction/debit-account-id ?debit-account-id-in])
           :in [?debit-account-id-in ?credit-account-id-in]
           :args [101 101]}
         (dtl/apply-criteria query
                             [:or
                              {:debit-account-id 101}
                              {:credit-account-id 101}]
                             {:model-type :transaction}))))

(deftest apply-union-and-intersection-together
  (testing "the 'and' is the outer conjunction"
    (is (= '{:find [?x]
             :in [?transaction-date-in
                  ?debit-account-id-in
                  ?credit-account-id-in]
             :args ["2000-01-01" 101 101] ; TODO: maybe unify the two same variables?
             :where  [[?x :transaction/transaction-date ?transaction-date-in]
                      (or [?x :transaction/debit-account-id ?debit-account-id-in]
                          [?x :transaction/credit-account-id ?credit-account-id-in])]}
           (dtl/apply-criteria query
                               [:and
                                {:transaction-date "2000-01-01"}
                                [:or
                                 {:debit-account-id 101}
                                 {:credit-account-id 101}]]
                               {:model-type :transaction}))))
  (testing "the 'or' is the outer conjunction"
    (is false "Need to adjust the test below to account for two inputs with the same name")
    #_(is (= '{:find [?x]
             :in [?debit-account-id-in
                  ?transaction-date-in
                  ?credit-account-id-in
                  ?transaction-date-in]
             :args [101
                    "2000-01-01"
                    101
                    "2000-01-01"] ; TODO: maybe unify the two same variables?
             :where  (or [[?x :transaction/credit-account-id ?credit-account-id-in]
                          [?x :transaction/transaction-date ?transaction-date-in]]
                         [[?x :transaction/debit-account-id ?debit-account-id-in]
                          [?x :transaction/transaction-date ?transaction-date-in]])}
           (dtl/apply-criteria query
                               [:or
                                {:debit-account-id 101
                                 :transaction-date "2000-01-01"}
                                {:credit-account-id 101
                                 :transaction-date "2000-01-01"}]
                               {:model-type :transaction})))))


(deftest apply-options
  (testing "limit"
    (is (= '{:find [?x]
             :limit 1}
           (dtl/apply-options query
                              {:limit 1}))
        "The limit attribute is copied"))
  (testing "sorting"
    (is (= '{:find [?x ?size]
             :where [[?x :shirt/size ?size]]
             :order-by [[?size :asc]]}
           (dtl/apply-options query
                              {:order-by :size}
                              {:model-type :shirt}))
        "A single column is symbolized and ascended is assumed")
    (is (= '{:find [?x ?size]
             :where [[?x :shirt/size ?size]]
             :order-by [[?size :desc]]}
           (dtl/apply-options query
                              {:order-by [[:size :desc]]}
                              {:model-type :shirt}))
        "An explicit direction is copied")
    (is (= '{:find [?x ?size ?weight]
             :where [[?x :shirt/size ?size]
                     [?x :shirt/weight ?weight]]
             :order-by [[?size :asc]
                        [?weight :desc]]}
           (dtl/apply-options query
                              {:order-by [:size [:weight :desc]]}
                              {:model-type :shirt}))
        "Multiple fields are handled appropriately")))
