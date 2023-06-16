(ns xtdb-money.models.datomic.accounts
  (:require [xtdb-money.datomic :as d]))

(defmethod d/before-save :account
  [{:keys [first-trx-date last-trx-date] :as account}]
  (cond-> (update-in account [:type] name)
    (not first-trx-date) (dissoc :first-trx-date)
    (not last-trx-date)  (dissoc :last-trx-date)))

(defmethod d/after-read :account
  [account]
  (-> account
      (update-in [:entity-id] :id)
      (update-in [:type] keyword)))

(defmethod d/criteria->query :account
  [criteria {::d/keys [db]}]
  {:query '[:find (pull ?a [*])
            :in $ ?a]
   :args [db (:id criteria)]})
