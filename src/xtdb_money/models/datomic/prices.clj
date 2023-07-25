(ns xtdb-money.models.datomic.prices
  (:require [xtdb-money.util :refer [->storable-date
                                     <-storable-date]]
            [xtdb-money.datomic :as d]))

(defmethod d/before-save :price
  [price]
  (update-in price [:trade-date] ->storable-date))

(defmethod d/after-read :price
  [price]
  (update-in price [:trade-date] <-storable-date))
