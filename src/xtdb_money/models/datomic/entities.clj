(ns xtdb-money.models.datomic.entities
  (:require [xtdb-money.datomic :as d]))

(defn- apply-id
  [query {:keys [id]}]
  (if id
    (-> query
        (update-in [:query :in] conj '?e)
        (update-in [:args] conj id))
    query))

(defmethod d/criteria->query :entity
  [criteria _opts]
  (-> {:query '{:find [(pull ?e [*])]
                :in [$]
                :where []}
       :args []}
      (apply-id criteria)))
