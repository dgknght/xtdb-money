(ns xtdb-money.models.prices-test
  (:require [clojure.test :refer [is use-fixtures ]]
            [clj-time.core :as t]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.test-context :refer [with-context
                                             find-commodity]]
            [xtdb-money.helpers :refer [reset-db
                                        dbtest]]
            [xtdb-money.models.prices :as prcs]
            [xtdb-money.models.mongodb.ref]
            [xtdb-money.models.sql.ref]
            [xtdb-money.models.xtdb.ref]
            [xtdb-money.models.datomic.ref]))

(use-fixtures :each reset-db)

(def ^:private create-ctx
  {:entities [{:name "Personal"}]
   :commodities [{:entity-id "Personal"
                  :type :currency
                  :name "United States Dollar"
                  :symbol "USD"}
                 {:entity-id "Personal"
                  :type :stock
                  :name "Apple Inc"
                  :symbol "AAPL"}]})

(dbtest create-a-price
  (with-context create-ctx
    (let [commodity (find-commodity "AAPL")
          price {:commodity-id (:id commodity)
                 :trade-date (t/local-date 2020 6 1)
                 :value 12.34M}
          result (prcs/put price)]
      (is (:id result)
          "The result contains an ID value")
      (is (comparable? price result)
          "The return value contains the expected attributes")
      (is (comparable? price (prcs/find (:id result)))
          "The price can be retrieved by :id"))))

(def ^:private find-ctx
  (-> create-ctx
      (update-in [:commodities] conj {:entity-id "Personal"
                                      :type :stock
                                      :name "Microsoft"
                                      :symbol "MSFT"})
      (assoc :prices [{:commodity-id "AAPL"
                       :trade-date (t/local-date 2000 6 1)
                       :value 12.34M}
                      {:commodity-id "AAPL"
                       :trade-date (t/local-date 2000 7 1)
                       :value 13.45M}
                      {:commodity-id "AAPL"
                       :trade-date (t/local-date 1999 7 1)
                       :value 11M}
                      {:commodity-id "MSFT"
                       :trade-date (t/local-date 2000 7 1)
                       :value 9.99M}])))

(dbtest find-by-commodity
   (with-context find-ctx
     (let [commodity (find-commodity "AAPL")]
       (assert commodity "Unable to find the commodity")
       (is (seq-of-maps-like? [{:trade-date (t/local-date 2000 7 1)
                                :value 13.45M}
                               {:trade-date (t/local-date 2000 6 1)
                                :value 12.34M}]
                              (prcs/find {:commodity-id (:id commodity)
                                          :trade-date [:and
                                                       [:>= (t/local-date 2000 1 1)]
                                                       [:< (t/local-date 2001 1 1)]]}))
           "The prices for the commodity are returned"))))
