(ns xtdb-money.models.xtdb.entities
  (:require [xtdb-money.xtdb :as x]))

(defmethod x/identifying-where-clause :entity
  [_]
  '[?x :entity/name ?name])
