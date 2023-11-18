(ns xtdb-money.mongodb.queries-test
    (:require [clojure.test :refer [deftest is are]]
              [xtdb-money.mongodb.queries :as qrs]))

(deftest create-a-simple-query
    (is (= {:where {:name "John"}}
           (qrs/apply-criteria {} {:name "John"}))))

(deftest apply-a-comparison-criterion
    (are [criteria exp] (= exp (qrs/apply-criteria {} criteria))
         {:age [:> 21]}
         {:where {:age {:$gt 21}}}

         {:age [:>= 21]}
         {:where {:age {:$gte 21}}}

         {:age [:< 21]}
         {:where {:age {:$lt 21}}}

         {:age [:<= 21]}
         {:where {:age {:$lte 21}}}))

(deftest apply-an-elem-match-criterion
    (is (= {:where
            {:identities
             {:$elemMatch
              {:oauth-provider "google"
               :oauth-id "abc123"}}}}
           (qrs/apply-criteria {}
                               {:identities
                                {:oauth-provider "google"
                                 :oauth-id "abc123"}}))))

(deftest apply-a-sort
    (are [options exp] (= exp (qrs/apply-options {} options))
         {:sort :surname}
         {:sort {"surname" 1}}

         {:sort [:surname]}
         {:sort {"surname" 1}}

         {:sort [[:surname :asc]]}
         {:sort {"surname" 1}}

         {:sort [[:surname :desc]]}
         {:sort {"surname" -1}}))

(deftest apply-a-limit
    (is (= {:limit 10}
           (qrs/apply-options {} {:limit 10}))))

(deftest apply-an-offset
    (are [opts exp] (= exp (qrs/apply-options {} opts))
         {:skip 20}
         {:skip 20}

         {:offset 20}
         {:skip 20}))
