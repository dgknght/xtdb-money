(ns xtdb-money.models.xtdb.transactions
  (:require [clojure.walk :refer [prewalk]]
            [clojure.set :refer [rename-keys]]
            [xtdb-money.xtdb :as x]
            [xtdb-money.util :refer [->storable-date
                                     <-storable-date]]))

(defmethod x/before-save :transaction
  [transaction]
  (update-in transaction [:transaction-date] ->storable-date))

(defmethod x/after-read :transaction
  [transaction]
  (update-in transaction [:transaction-date] <-storable-date))

(defmethod x/prepare-criteria :transaction
  [criteria]
  ; TODO: Validate the criteria, ensure a transaction date is specified
  (prewalk (fn [x]
             (if (map? x)
               (rename-keys x {:account-id #{:debit-account-id :credit-account-id}})
               x))
           criteria))
