(ns xtdb-money.models.xtdb.entities
  (:require [xtdb-money.xtdb :as x]))

(defn- apply-id
  [query {:keys [id]}]
  (if id
    (-> query
        (update-in [::x/args] (fnil conj []) id)
        (update-in [:in] (fnil conj '[$]) '?id)
        (update-in [:where] conj '[?e :xt/id ?id]))
    query))

(defn- apply-options
  [query {:keys [limit]}]
  (if limit
    (assoc query :limit limit)
    query))

(defmethod x/criteria->query :entity
  [criteria options]
  (-> '{:find [(pull ?e [*])]
        :where [[?e :entity/name ?name]]}
      (apply-id criteria)
      (apply-options options)))
