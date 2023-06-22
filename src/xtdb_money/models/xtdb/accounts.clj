(ns xtdb-money.models.xtdb.accounts
  (:require [xtdb-money.xtdb :as x]))

(def ^:private query-base
  '{:find [(pull ?a [*])]
    :in [$]
    ::x/args []
    :where [[?a :account/type ?type]]})

(defn- apply-id
  [query {:keys [id]}]
  (if id
    (-> query
        (update-in [::x/args] conj id)
        (update-in [:in] conj '?id)
        (update-in [:where] conj '[?a :xt/id ?id]))
    query))

(defn- apply-entity-id
  [query {:keys [entity-id]}]
  (if entity-id
    (-> query
        (update-in [::x/args] conj entity-id)
        (update-in [:in] conj '?entity-id)
        (update-in [:where] conj '[?a :account/entity-id ?entity-id]))
    query))

(defn- apply-options
  [query {:keys [limit]}]
  (if limit
    (assoc query :limit limit)
    query))

(defmethod x/criteria->query :account
  [criteria options]
  (-> query-base
      (apply-id criteria)
      (apply-entity-id criteria)
      (apply-options options)))
