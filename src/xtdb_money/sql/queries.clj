(ns xtdb-money.sql.queries
  (:refer-clojure :exclude [format])
  (:require [clojure.pprint :refer [pprint]]
            [honey.sql :refer [format]]
            [honey.sql.helpers :refer [select
                                       from
                                       where]]
            [dgknght.app-lib.core :refer [update-in-if]]
            [dgknght.app-lib.inflection :refer [plural]]
            [xtdb-money.core :as mny]
            [xtdb-money.sql.types :refer [->storable
                                          coerce-id]]))

(derive clojure.lang.PersistentArrayMap ::map)
(derive clojure.lang.PersistentVector ::vector)

(def infer-table-name
  (comp plural
        mny/model-type))

(defmulti ^:private ->statement (fn [[_k v]]
                                  (type v)))

(defmethod ->statement :default
  [[k v]]
  [:= k (->storable v)])

; This can look like
; [:date [:and [:>= start-date] [:< end-date]]]
; and change it to
; [:and [:>= :date start-date] [:< :date end-date]]
;
; And it can look like
; [:date [:>= start-date]]
; and change it to
; [:>= :date start-date]
(defmethod ->statement ::vector
  [[k [oper & vs]]]
  (case oper
    (:and :or)      (apply vector oper (mapv (fn [[op v]] [op k (->storable v)]) vs))
    (:< :<= :>= :>) [oper k (->storable (->storable (first vs)))]))

(defmulti ^:private ->where type)

(defmethod ->where ::map
  [m]
  (let [converted (mapv ->statement m)]
    (if (= 1 (count m))
      (first converted)
      (apply vector :and converted))))

(defmethod ->where ::vector
  [[oper & stmts]]
  (apply vector oper (map ->where stmts)))

(defn- apply-criteria
  [s criteria]
  (if (empty? criteria)
    s
    (where s (->where criteria))))

(defn- apply-options
  [s {:keys [limit order-by]}]
  (cond-> s
    limit (assoc :limit limit)
    order-by (assoc :order-by order-by)))

(defn criteria->query
  [criteria & [options]]
  (-> (select :*)
      (from (infer-table-name criteria))
      (apply-criteria (update-in-if criteria [:id] coerce-id))
      (apply-options options)
      format))
