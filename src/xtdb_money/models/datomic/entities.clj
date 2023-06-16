(ns xtdb-money.models.datomic.entities
  (:require [xtdb-money.datomic :as d]))

(defmethod d/criteria->query :entity
  [criteria {::d/keys [db]}]
  {:query '[:find (pull ?e [*])
            :in $ ?e]
   :args (filter identity [db
                           (:id criteria)])})
