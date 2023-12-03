(ns xtdb-money.mongodb.queries
  (:require [dgknght.app-lib.core :refer [update-in-if]]
            [clojure.set :refer [rename-keys]]
            [somnium.congomongo.coerce :refer [coerce-ordered-fields]]
            [xtdb-money.mongodb.types :refer [coerce-id]]))

(derive clojure.lang.PersistentVector ::vector)
(derive clojure.lang.PersistentArrayMap ::map)
(derive clojure.lang.Keyword ::keyword)

(def ^:private oper-map
  {:> :$gt
   :>= :$gte
   :< :$lt
   :<= :$lte})

(defmulti adjust-complex-criterion
  (fn [[_k v]]
    (cond
      (map? v)
      ::map

      (vector? v)
      (let [[oper] v]
        (or (#{:and :or :=} oper)
            (when (oper-map oper) :comparison)
            (first v))))))

(defn- ->mongodb-op
  [op]
  (get-in oper-map
          [op]
          (keyword (str "$" (name op)))))

(declare adjust-complex-criteria)

(defmethod adjust-complex-criterion :default [c] c)

(defmethod adjust-complex-criterion :comparison
  [[f [op v]]]
  ; e.g. [:transaction-date [:< #inst "2020-01-01"]]
  ; ->   [:transaction-date {:$lt #inst "2020-01-01"}]
  {f {(->mongodb-op op) v}})

(defmethod adjust-complex-criterion :and
  [[f [_ & cs]]]
  {f (->> cs
          (map #(update-in % [0] ->mongodb-op))
          (into {}))})

(defmethod adjust-complex-criterion :or
  [[f [_ & cs]]]
  {f {:$or (mapv (fn [[op v]]
                   {(->mongodb-op op) v})
                 cs)}})

(defmethod adjust-complex-criterion :=
  [[f [_ v]]]
  {f (if (map? v)
       {:$elemMatch (adjust-complex-criteria v)}
       v)})

(defmethod adjust-complex-criterion ::map
  [[f m]]
  {f {:$elemMatch m}})

(defn- adjust-complex-criteria
  [criteria]
  (->> criteria
       (map adjust-complex-criterion)
       (into {})))

(defmulti criteria->query type)

(defmethod criteria->query ::map
  [criteria]
  (-> criteria
      (update-in-if [:id] coerce-id)
      (rename-keys {:id :_id})
      adjust-complex-criteria))

(defmethod criteria->query ::vector
  [[oper & cs]]
  (case oper
    :or {:$or (mapv criteria->query cs)}
    :and {:$and (mapv criteria->query cs)}))

(defn apply-criteria
  [query criteria]
  (if (seq criteria)
    (assoc query :where (criteria->query criteria))
    query))

(defmulti ^:private ->mongodb-sort
  (fn [x]
    (when (vector? x)
      :explicit)))

(defmethod ->mongodb-sort :default
  [x]
  [x 1])

(defmethod ->mongodb-sort :explicit
  [sort]
  (update-in sort [1] #(if (= :asc %) 1 -1)))

(defmulti ^:private ->sort type)

(defmethod ->sort :default
  [attrs]
  (->> attrs
       (map ->mongodb-sort)
       coerce-ordered-fields))

(defmethod ->sort ::keyword
  [attr]
  (->sort [attr]))

(defn apply-options
  [query {:keys [limit
                 order-by
                 sort
                 skip
                 offset]}]
  (cond-> query
    limit (assoc :limit limit)
    skip (assoc :skip skip)
    offset (assoc :skip offset)
    order-by (assoc :sort (->sort order-by))
    sort (assoc :sort (->sort sort))))
