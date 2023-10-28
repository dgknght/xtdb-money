(ns xtdb-money.sql
  (:refer-clojure :exclude [update])
  (:require [clojure.tools.logging :as log]
            [clj-time.coerce :refer [to-sql-date
                                     to-local-date]]
            [next.jdbc :as jdbc]
            [next.jdbc.plan :refer [select!]]
            [next.jdbc.sql.builder :refer [for-insert
                                           for-update
                                           for-delete]]
            [honey.sql.helpers :as h]
            [honey.sql :as hsql]
            [dgknght.app-lib.inflection :refer [plural]]
            [dgknght.app-lib.core :refer [update-in-if]]
            [xtdb-money.core :as mny])
  (:import org.joda.time.LocalDate
           java.sql.Date
           java.lang.String))

(defmulti coerce-id type)

(defmethod coerce-id :default [id] id)

(defmethod coerce-id String
  [id]
  (Long/parseLong id))

(defmulti ->storable type)
(defmethod ->storable :default [x] x)
(defmethod ->storable LocalDate
  [d]
  (to-sql-date d))

(defmulti <-storable type)
(defmethod <-storable :default [x] x)
(defmethod <-storable Date
  [d]
  (to-local-date d))

(defn- dispatch
  [_db model & _]
  (mny/model-type model))

(defmulti insert dispatch)
(defmulti select dispatch)
(defmulti update dispatch)

(defmulti before-save mny/model-type)
(defmethod before-save :default [m] m)

(defmulti deconstruct mny/model-type)
(defmethod deconstruct :default [m] [m])

(def ^:private infer-table-name
  (comp plural
        mny/model-type))

(defmethod insert :default
  [db model]
  {:pre [(mny/model-type model)]}
  (let [table (infer-table-name model)
        s (for-insert table
                      model
                      jdbc/snake-kebab-opts)
        result (jdbc/execute-one! db s {:return-keys [:id]})]

    ; TODO: scrub for sensitive data
    (log/debugf "database insert %s -> %s" model s)

    (get-in result [(keyword (name table) "id")])))

(defmethod update :default
  [db {:keys [id] :as model}]
  (let [table (infer-table-name model)
        s (for-update table
                      (dissoc model :id)
                      {:id id}
                      jdbc/snake-kebab-opts)
        result (jdbc/execute-one! db s {:return-keys [:id]})]

    ; TODO: scrub sensitive data
    (log/debugf "database update %s -> %s" model s)

    (get-in result [(keyword (name table) "id")])))

(defmulti apply-criteria (fn [_s c] (mny/model-type c)))

(defmulti apply-criterion
  (fn [_s _k v]
    (when (vector? v)
      (case (first v)
        (:< :<= :> :>= :!=) :explicit-oper
        (:and :or)          :conjunction
        (throw (ex-info (str "Unknown operator: " (first v))
                        {:criterion v}))))))

(defmethod apply-criterion :default
  [s k v]
  (h/where s [:= k (->storable v)]))

(defmethod apply-criterion :explicit-oper
  [s k [oper v]]
  (h/where s [oper k (->storable v)]))

(defmethod apply-criterion :conjunction
  [s k [oper & stmts]]
  (apply h/where s oper (map (fn [[op v]]
                               [op k (->storable v)])
                             stmts)))

(defmethod apply-criteria :default
  [s criteria]
  (if (empty? criteria)
    s
    (reduce-kv apply-criterion
               s
               criteria)))

(defn- apply-options
  [s {:keys [limit order-by]}]
  (cond-> s
    limit (assoc :limit limit)
    order-by (assoc :order-by order-by)))

(defmulti after-read mny/model-type)
(defmethod after-read :default [m] m)

(defmulti attributes identity)

(defmethod select :default
  [db criteria options]
  (let [query (-> (h/select :*)
                  (h/from (infer-table-name criteria))
                  (apply-criteria (update-in-if criteria [:id] coerce-id))
                  (apply-options options)
                  hsql/format)]

    ; TODO: scrub sensitive data
    (log/debugf "database select %s with options %s -> %s" criteria options query)

    (map (comp after-read
               (mny/+model-type criteria))
         (select! db
                  (attributes (mny/model-type criteria))
                  query
                  jdbc/snake-kebab-opts))))

(defn delete-one
  [db m]
  (let [s (for-delete (infer-table-name m)
                      (select-keys m [:id])
                      {})]

    ; TODO: scrub sensitive data
    (log/debugf "database delete %s -> %s" m s)

    (jdbc/execute! db s)))

(defn- wrap-oper
  [m]
  (if (vector? m)
    m
    [(if (and (:id m)
              (not (uuid? (:id m))))
       ::mny/update
       ::mny/insert)
     m]))

(defn- put-one
  [db [oper model]]
  (case oper
    ::mny/insert (insert db model)
    ::mny/update (update db model)
    ::mny/delete (delete-one db model)))

; this is not unlike the way datomic handles temporary ids
; if saving multiple models that need to reference each other
; before the database has issued an ID, we can specify temporary
; ids that will be resolved as needed during the save process
(defmulti resolve-temp-ids
  (fn [model _id-map]
    (mny/model-type model)))

(defmethod resolve-temp-ids :default
  [model _id-map]
  model)

(defn- temp-id?
  [{:keys [id]}]
  (uuid? id))

(defn- execute-and-aggregate
  [db {:as result :keys [id-map]} [operator m]]
  (let [ready-to-save (cond-> (resolve-temp-ids m id-map)
                        (temp-id? m) (dissoc :id))
        saved (put-one db [operator ready-to-save])]
    (cond-> (update-in result [:saved] conj saved)
      (temp-id? m)
      (assoc-in [:id-map (:id m)]
                saved))))

(defn put*
  [db models]
  ; TODO: refactor this to handle temporary ids
  (jdbc/with-transaction [tx db]
    (:saved (->> models
                 (mapcat deconstruct)
                 (map (comp wrap-oper
                            before-save))
                 (reduce (partial execute-and-aggregate tx)
                         {:id-map {}
                          :saved []})))))

(defn select*
  [db criteria options]
  (select db criteria options))

(defn- delete*
  [db models]
  (jdbc/with-transaction [tx db]
    (doseq [m (map #(update-in % [:id] coerce-id)
                    models)]
      (put-one tx [::mny/delete m]))))

(defn- reset*
  [db]
  (jdbc/execute! db ["truncate table users cascade"]))

(defmethod mny/reify-storage :sql
  [config]
  (let [db (jdbc/get-datasource config)]
    (reify mny/Storage
      (put [_ models]       (put* db models))
      (select [_ crit opts] (select* db crit opts))
      (delete [_ models]    (delete* db models))
      (close [_])
      (reset [_]            (reset* db)))))
