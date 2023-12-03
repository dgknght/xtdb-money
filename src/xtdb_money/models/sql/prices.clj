(ns xtdb-money.models.sql.prices
  (:require [xtdb-money.sql :as sql]
            [xtdb-money.sql.types :refer [->storable
                                          <-storable]]))

(defmethod sql/before-save :price
  [price]
  (update-in price [:trade-date] ->storable))

(defmethod sql/after-read :price
  [price]
  (update-in price [:trade-date] <-storable))

(defmethod sql/attributes :price [_]
  [:id :commodity-id :trade-date :value])
