(ns xtdb-money.mongodb
  (:require [clojure.tools.logging :as log]
            [clojure.set :refer [rename-keys]]
            [clj-time.coerce :refer [to-local-date
                                     to-date]]
            [cheshire.generate :refer [add-encoder]]
            [somnium.congomongo :as m]
            [somnium.congomongo.coerce :refer [ConvertibleFromMongo
                                               ConvertibleToMongo
                                               coerce-ordered-fields]]
            [dgknght.app-lib.inflection :refer [plural]]
            [dgknght.app-lib.core :refer [update-in-if]]
            [xtdb-money.core :as mny])
  (:import org.joda.time.LocalDate
           java.util.Date
           org.bson.types.Decimal128
           org.bson.types.ObjectId
           com.fasterxml.jackson.core.JsonGenerator))

(derive clojure.lang.PersistentVector ::vector)
(derive clojure.lang.PersistentArrayMap ::map)

(add-encoder ObjectId
             (fn [^ObjectId id ^JsonGenerator g]
               (.writeString g (str id))))

(extend-protocol ConvertibleToMongo
  LocalDate
  (clojure->mongo [^LocalDate d] (to-date d)))

(extend-protocol ConvertibleFromMongo
  Date
  (mongo->clojure [^Date d _kwd] (to-local-date d))

  Decimal128
  (mongo->clojure [^Decimal128 d _kwd] (.bigDecimalValue d)))

(defmulti coerce-id type)
(defmethod coerce-id :default [id] id)
(defmethod coerce-id String
  [id]
  (ObjectId. id))

(defn- safe-coerce-id
  [id]
  (when id (coerce-id id)))

(defmulti before-save mny/model-type)
(defmethod before-save :default [m] m)
(defmulti after-read mny/model-type)
(defmethod after-read :default [m] m)

(def ^:private infer-collection-name
  (comp plural
        mny/model-type))

(defmulti transact
  (fn [_conn [op _m]]
    op))

(defmethod transact ::mny/insert
  [conn [_ m]]
  (m/with-mongo conn
    (let [col-name (infer-collection-name m)]
      ; TODO: redact sensitive data
      (log/debugf "insert into %s model %s" col-name m)
      (m/insert! col-name m))))

(defmethod transact ::mny/update
  [conn [_ m]]
  (m/with-mongo conn
    (m/update! (infer-collection-name m)
               {:_id (:id m)}
               {:$set (dissoc m :id)})
    (:id m)))

(defmethod transact ::mny/delete
  [conn [_ m]]
  (m/with-mongo conn
    (m/destroy! (infer-collection-name m)
                {:_id (:id m)})))

(defn- wrap-oper
  [m]
  (if (vector? m)
    m
    (if (:id m)
      [::mny/update m]
      [::mny/insert m])))

(defn- put*
  [conn models]
  (mapv (comp #(if (map? %)
                 (rename-keys % {:_id :id})
                 %)
              #(transact conn %)
              wrap-oper
              before-save)
        models))

(def ^:private oper-map
  {:> :$gt
   :>= :$gte
   :< :$lt
   :<= :$lte})

(defmulti adjust-complex-criterion
  (fn [[_k v]]
    (when (vector? v)
      (let [[oper] v]
        (or (#{:and :or} oper)
            (when (oper-map oper) :comparison)
            (first v))))))

(defn- ->mongodb-op
  [op]
  (get-in oper-map
          [op]
          (keyword (str "$" (name op)))))

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

; TODO: merge this with a recursive call to criteria->query
(defn- adjust-complex-criteria
  [criteria]
  (->> criteria
       (map adjust-complex-criterion)
       (into {})))

(defmulti ^:private criteria->query type)

(defmethod criteria->query ::map
  [criteria]
  (-> criteria
      (update-in-if [:id] coerce-id)
      (rename-keys {:id :_id})
      adjust-complex-criteria))

(defmethod criteria->query ::vector
  [[oper & [c :as cs]]]
  (case oper
    :or {:$or (mapv criteria->query cs)}
    :and {:$and (mapv criteria->query cs)}
    := (if (map? c)
         {:$elemMatch c}
         c)))

(defn apply-criteria
  [query criteria]
  (if (seq criteria)
    (assoc query :where (criteria->query criteria))
    query))

(defn apply-account-id
  [{:keys [where] :as query} {:keys [account-id]}]
  (if-let [id (safe-coerce-id account-id)]
    (let [c {:$or
             [{:debit-account-id id}
              {:credit-account-id id}]}]
      (assoc query :where (if where
                            {:$and [where c]}
                            c)))
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

(defn apply-options
  [query {:keys [limit order-by]}]
  (cond-> query
    limit (assoc :limit limit)
    order-by (assoc :sort (coerce-ordered-fields (map ->mongodb-sort order-by)))))

(defmulti prepare-criteria mny/model-type)
(defmethod prepare-criteria :default [c] c)

(defn- select*
  [conn criteria options]
  (m/with-mongo conn
    (let [query (-> {}
                    (apply-criteria (prepare-criteria criteria))
                    (apply-options options))]
      (log/debugf "fetch %s with options %s -> %s" criteria options query)
      (map (comp after-read
                 #(rename-keys % {:_id :id})
                 #(mny/model-type % criteria))
           (m/fetch (infer-collection-name criteria)
                    query)))))

(defn- delete*
  [conn models]
  (m/with-mongo conn
    (let [coll-name (infer-collection-name (first models))]
      (doseq [query (map (comp #(hash-map :_id %)
                               :id)
                         models)]
        (log/debugf "delete %s" query)
        (m/destroy! coll-name query)))))

(defn- reset*
  [conn]
  (m/with-mongo conn
    (doseq [c [:transactions :accounts :entities :users]]
      (m/destroy! c {}))))

(defn connect
  [{:keys [database] :as config}]
  (m/make-connection database (dissoc config :database)))

(defmethod mny/reify-storage :mongodb
  [config]
  (let [conn (connect config)]
    (reify mny/Storage
      (put [_ models]       (put* conn models))
      (select [_ crit opts] (select* conn crit opts))
      (delete [_ models]    (delete* conn models))
      (close [_])
      (reset [_]            (reset* conn)))))
