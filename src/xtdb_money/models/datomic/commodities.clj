(ns xtdb-money.models.datomic.commodities
  (:require [xtdb-money.datomic :as d]))

(defmethod d/bounding-where-clause :commodity
  [_]
  '[?x :commodity/symbol ?symbol])
