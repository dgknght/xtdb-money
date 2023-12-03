(ns xtdb-money.mongodb-test
  (:require [clojure.test :refer [deftest is]]
            [xtdb-money.mongodb :as mdb]))

(deftest apply-criteria-to-a-query-map
  (is (= {}
         (mdb/apply-criteria {} nil))
      "A nil criteria is ignored")
  (is (= {}
         (mdb/apply-criteria {} {}))
      "An empty criteria is ignored")
  (is (= {:where {:entity-id 101}}
         (mdb/apply-criteria {}
                             {:entity-id 101}))
      "A scalar map value is a simple equality test")
  (is (= {:where {:transaction-date {:$lt "end"}}}
         (mdb/apply-criteria {}
                             {:transaction-date
                              [:< "end"]}))
      "A :< is converted to :$lt")
  (is (= {:where {:transaction-date {:$lte "end"}}}
         (mdb/apply-criteria {}
                             {:transaction-date
                              [:<= "end"]}))
      "A :<= is converted to :$lte")
  (is (= {:where {:transaction-date {:$gt "start"}}}
         (mdb/apply-criteria {}
                             {:transaction-date
                              [:> "start"]}))
      "A :< is converted to :$gt")
  (is (= {:where {:transaction-date {:$gte "start"}}}
         (mdb/apply-criteria {}
                             {:transaction-date
                              [:>= "start"]}))
      "A :<= is converted to :$lte")
  (is (= {:where {:transaction-date {:$gte "start"
                                     :$lt "end"}}}
         (mdb/apply-criteria {}
                             {:transaction-date
                              [:and
                               [:>= "start"]
                               [:< "end"]]}))
      "An intersection of two criteria a translated to mongodb operators")
  (is (= {:where {:transaction-date {:$or
                                     [{:$gte "start"}
                                      {:$lt "end"}]}}}
         (mdb/apply-criteria {}
                             {:transaction-date
                              [:or
                               [:>= "start"]
                               [:< "end"]]}))
      "A union of two criteria a translated to mongodb operators")
  (is (= {:where {:$or [{:debit-account-id 1}
                        {:credit-account-id 1}]}}
         (mdb/apply-criteria {}
                             [:or
                              {:debit-account-id 1}
                              {:credit-account-id 1}]))
      "A top-level conjunction is converted to a mongodb conjunction")
  (is (= {:where {:identities {:$elemMatch {:oauth-provider "google"
                                            :oauth-id "abc123"}}}}
         (mdb/apply-criteria {}
                             {:identities [:= {:oauth-provider "google"
                                               :oauth-id "abc123"}]}))
      "Equality to a map is translated to $elemMatch"))

(deftest union-of-account-id
  (is (= {:where {:$or
                  [{:debit-account-id 101}
                   {:credit-account-id 101}]}}
         (mdb/apply-account-id {}
                               {:account-id 101}))
      "An :account-id is compared against debit or credit account id")
  (is (= {:where {:$and
                  [{:entity-id 201}
                   {:$or [{:debit-account-id 101}
                          {:credit-account-id 101}]}]}}
         (mdb/apply-account-id {:where {:entity-id 201}}
                               {:account-id 101}))
      "The existing where clause is preserved and an intersection is specified"))

(deftest apply-a-limit-to-a-query
  (is (= {:limit 1}
         (mdb/apply-options {} {:limit 1}))))

(deftest specify-sort-order-for-a-query
  (is (= {:sort {"transaction-date" 1}}
         (mdb/apply-options {} {:order-by [:transaction-date]}))
      "A simple field spec is copied directly")
  (is (= {:sort {"transaction-date" 1}}
         (mdb/apply-options {} {:order-by [[:transaction-date :asc]]}))
      ":asc is translated to 1")
  (is (= {:sort {"transaction-date" -1}}
         (mdb/apply-options {} {:order-by [[:transaction-date :desc]]}))
      ":desc is translated to -1")
  ; TODO: I'm not convinced that this will work
  (is (= {:sort {"transaction-date" -1
                 "index" 1}}
         (mdb/apply-options {} {:order-by [[:transaction-date :desc]
                                           [:index :asc]]}))
      ":desc is translated to -1"))
