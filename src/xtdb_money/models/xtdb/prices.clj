(ns xtdb-money.models.xtdb.prices
  (:require [xtdb-money.xtdb :as x]
            [xtdb-money.util :refer [->storable-date
                                     <-storable-date]]))

(defmethod x/before-save :price
  [price]
  (update-in price [:trade-date] ->storable-date))

(defmethod x/after-read :price
  [price]
  (update-in price [:trace-date] <-storable-date))
