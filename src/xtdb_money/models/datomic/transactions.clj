(ns xtdb-money.models.datomic.transactions
  (:require [clojure.pprint :refer [pprint]]
            [clojure.walk :refer [prewalk]]
            [clojure.set :refer [rename-keys]]
            [xtdb-money.util :refer [->storable-date
                                     <-storable-date]]
            [xtdb-money.datomic :as d])
  (:import org.joda.time.LocalDate))

(defmulti ->storable type)

(defmethod ->storable :default [v] v)

(defmethod ->storable LocalDate
  [v]
  (->storable-date v))

(defmethod d/prepare-criteria :transaction
  [criteria]

  (prewalk (fn [x]
             (if (map? x)
               (rename-keys x {:account-id #{:debit-account-id :credit-account-id}})
               x))
           criteria))

(defmethod d/before-save :transaction
  [trx]
  (-> trx
      (update-in [:transaction-date] ->storable-date)
      (select-keys [:id
                    :entity-id
                    :transaction-date
                    :correlation-id
                    :debit-account-id
                    :debit-index
                    :debit-balance
                    :credit-account-id
                    :credit-index
                    :credit-balance
                    :description
                    :quantity])))

(defmethod d/after-read :transaction
  [trx]
  (update-in trx [:transaction-date] <-storable-date))
