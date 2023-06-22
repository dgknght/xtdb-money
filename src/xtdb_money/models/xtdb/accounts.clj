(ns xtdb-money.models.xtdb.accounts
  (:require [xtdb-money.xtdb :as x]))

(def ^:private query-base
  '{:find [(pull ?a [*])]
    :where []})

(defn- apply-id
  [query {:keys [id]}]
  (if id
    (-> query
        (update-in [:in] (fnil conj []) '?id)
        (update-in [:where] conj '[?a :account/id ?id])
        (update-in [::args] (fnil conj []) id))
    query))

(defn- apply-entity-id
  [query {:keys [entity-id]}]
  (if entity-id
    (-> query
        (update-in [:in] (fnil conj []) '?entity-id)
        (update-in [:where] conj '[?a :account/entity-id ?entity-id])
        (update-in [::args] (fnil conj []) entity-id))
    query))

(defmethod x/criteria->query :account
  [criteria _]
  (-> query-base
      (apply-id criteria)
      (apply-entity-id criteria)))
