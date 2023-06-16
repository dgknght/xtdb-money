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

(defn- apply-id
  [query {:keys [id]}]
  (if id
    (-> query
        (update-in [:query :in] conj '?a)
        (update-in [:args] conj id))
    query))

(defn- apply-entity-id
  [query {:keys [entity-id]}]
  (if entity-id
    (-> query
        (update-in [:query :in] conj '?entity-id)
        (update-in [:query :where] conj '[?a :account/entity-id ?entity-id])
        (update-in [:args] conj entity-id))
    query))

(defmethod d/criteria->query :account
  [criteria _opts]
  (-> {:query '{:find [(pull ?a [*])]
                :in [$]
                :where [[?a :account/name ?name]]}
       :args []}
      (apply-id criteria)
      (apply-entity-id criteria)))
