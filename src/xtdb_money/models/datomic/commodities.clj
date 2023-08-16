(ns xtdb-money.models.datomic.commodities
  (:require [xtdb-money.datomic :as d]))

(defmethod d/identifying-where-clause :commodity
  [_]
  '[?x :commodity/symbol ?symbol])
