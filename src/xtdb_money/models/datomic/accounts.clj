(ns xtdb-money.models.datomic.accounts
  (:require [dgknght.app-lib.core :refer [update-in-if]]
            [xtdb-money.util :refer [->storable-date
                                     <-storable-date]]
            [xtdb-money.datomic :as d]))

(defmethod d/before-save :account
  [account]
  (-> account
      (update-in-if [:first-trx-date] ->storable-date)
      (update-in-if [:last-trx-date] ->storable-date)))

(defmethod d/after-read :account
  [account]
  (-> account
      (update-in-if [:first-trx-date] <-storable-date)
      (update-in-if [:last-trx-date] <-storable-date)
      (update-in [:entity-id] :id)))

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
