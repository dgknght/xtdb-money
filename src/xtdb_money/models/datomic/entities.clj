(ns xtdb-money.models.datomic.entities
  (:require [xtdb-money.datomic :as d]))

(defmethod d/criteria->query :entity
  [_criteria {::d/keys [db]}]
  {:query '[:find ?e ?name
            :keys id name
            :where [?e :entity/name ?name]]
   :args [db]})
