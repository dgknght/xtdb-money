(ns xtdb-money.models.xtdb.transactions
  (:require [xtdb-money.xtdb :as x]
            [xtdb-money.util :refer [->storable-date
                                     <-storable-date]]))

(defmethod x/before-save :transaction
  [transaction]
  (update-in transaction [:transaction-date] ->storable-date))

(defmethod x/after-read :transaction
  [transaction]
  (update-in transaction [:transaction-date] <-storable-date))

(defmethod x/prepare-criteria :transaction
  [{:as criteria :keys [account-id]}]
  ; TODO: Validate the criteria, ensure a transaction date is specified
  ; TODO: Also, the destructing above will not work if the criteria has an
  ; outer vector, like [:and
  ;                      {:user-id 1}
  ;                      {:account-id 2}]
  (if account-id
    [:or
     (dissoc criteria :account-id)
     {:debit-account-id account-id}
     {:credit-account-id account-id}]
    criteria))
