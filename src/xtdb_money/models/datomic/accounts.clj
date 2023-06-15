(ns xtdb-money.models.datomic.accounts
  (:require [xtdb-money.datomic :as d]))

(defmethod d/before-save :account
  [{:keys [first-trx-date last-trx-date] :as account}]
  (cond-> (update-in account [:type] name)
    (not first-trx-date) (dissoc :first-trx-date)
    (not last-trx-date)  (dissoc :last-trx-date)))

(defmethod d/after-read :account
  [account]
  (update-in account [:type] keyword))

(defmethod d/criteria->query :account
  [_criteria {::d/keys [db]}]
  {:query '[:find ?a ?entity-id ?name ?type ?balance
            :keys id entity-id name type balance
            :where [?a :account/entity-id ?entity-id]
            [?a :account/name ?name]
            [?a :account/type ?type]
            [?a :account/balance ?balance]]
   :args [db]})
