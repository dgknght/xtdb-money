(ns xtdb-money.models.datomic.entities
  (:require [xtdb-money.datomic :as d]))

(defmethod d/identifying-where-clause :entity
  [_]
  '[?x :entity/name ?name])
