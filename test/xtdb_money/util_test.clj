(ns xtdb-money.util-test
  (:require [clojure.test :refer [deftest testing is are]]
            [clj-time.core :as t]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.util :as utl]))

(deftest quality-map-keys
  (is (= {:entity/name "Personal"}
         (utl/qualify-keys ^{:model-type :entity} {:name "Personal"}))
      "Unqualified keys are qualified with the model type")
  (is (= {:db/id "x"}
         (utl/qualify-keys ^{:model-type :entity} {:db/id "x"}))
      "Qualified keys are left as-is"))

(deftest add-an-id
  (is (uuid? (:id (utl/+id {})))
      "An :id attribute is added to a map that does not have an :id attribute")
  (is (= "abc" (:id (utl/+id {} (constantly "abc"))))
      "If an id fn is supplied, it is used to generate the id")
  (let [id (random-uuid)]
    (is (= {:id id}
           (utl/+id {:id id}))
        "The :id attribute is unchanged in a map that already contains one")))

(deftest prepend-a-value
  (is (= [:new :a :b]
         (utl/prepend [:a :b] :new))
      "The value is added at the front of a vector")
  (is (= '(:new :a :b)
         (utl/prepend '(:a :b) :new))
      "The value is added at the front of a list"))

(deftest apply-sort-rule
  (let [d1 (t/local-date 2001 1 1)
        d2 (t/local-date 2002 1 1)
        d3 (t/local-date 2004 1 1)
        items [{:v 2 :d d1 :s "carrot"}
               {:v 1 :d d3 :s "banana"}
               {:v 3 :d d2 :s "apple"}]]
    (testing "Ascending sort on one string field, implicit direction"
      (is (seq-of-maps-like? [{:s "apple"}
                              {:s "banana"}
                              {:s "carrot"}]
                             (utl/apply-sort {:order-by [:s]}
                                             items))))
    (testing "Ascending sort on one integer field, implicit direction"
      (is (= items
             (utl/apply-sort {} items))))
    (testing "Ascending sort on one integer field, implicit direction"
      (is (seq-of-maps-like? [{:v 1}
                              {:v 2}
                              {:v 3}]
                             (utl/apply-sort {:order-by [:v]}
                                             items))))
    (testing "Ascending sort on one date field, implicit direction"
      (is (seq-of-maps-like? [{:d d1}
                              {:d d2}
                              {:d d3}]
                             (utl/apply-sort {:order-by [:d]}
                                             items))))
    (testing "Ascending sort on one integer field, explicit direction"
      (is (seq-of-maps-like? [{:v 1}
                              {:v 2}
                              {:v 3}]
                             (utl/apply-sort {:order-by [[:v :asc]]}
                                             items))))
    (testing "Descending sort on one integer field"
      (is (seq-of-maps-like? [{:v 3}
                              {:v 2}
                              {:v 1}]
                             (utl/apply-sort {:order-by [[:v :desc]]}
                                             items))))
    (testing "Multi-field sort"
      (is (seq-of-maps-like? [{:v 1 :d d3}
                              {:v 2 :d d1}
                              {:v 2 :d d2}
                              {:v 3 :d d2}]
                             (utl/apply-sort {:order-by [:v :d]}
                                             (conj items {:v 2 :d d2})))))))

(deftest separate-nils-from-a-model
  (is (= [{:present :here}
          [:absent]]
         (utl/split-nils {:present :here
                          :absent nil}))))

(deftest identify-a-scalar-value
  (are [input expected] (= expected (utl/scalar? input))
       1        true
       :keyword true
       "string" true
       {}       false
       []       false
       '()      false
       #{}      false))

(deftest identify-a-valid-id-value
  (are [input expected] (= expected (utl/valid-id? input))
       1             true
       (random-uuid) true
       "mything"     true
       :mything      true
       {:id 1}       false
       [1]           false
       #{1}          false))
