(ns xtdb-money.datomic
  (:require [clojure.set :refer [rename-keys]]
            [datomic.client.api :as d]
            [datomic.client.api.protocols :refer [Connection]]
            [clj-time.coerce :as tc]
            [xtdb-money.datalog :as dtl]
            [xtdb-money.util :refer [qualify-keys
                                     unqualify-keys
                                     +id
                                     prepend
                                     apply-sort
                                     split-nils]]
            [xtdb-money.core :as mny])
  (:import org.joda.time.LocalDate))

(def schema
  [
   ; Entity
   {:db/ident :entity/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The name of the entity"}
   {:db/ident :entity/default-commodity-id
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Identifies the commodity to be used when no commodity is specified within the entity"}
   
   ; Commodity
   {:db/ident :commodity/entity-id
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Identifies the entity to which the commodity belongs"}
   {:db/ident :commodity/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The name of the commodity"}
   {:db/ident :commodity/symbol
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The symbol for the commodity"}
   {:db/ident :commodity/type
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/doc "The type of the commodity (currency, stock, fund)"}
   
   ; Price
   {:db/ident :price/commodity-id
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Identifies the commodity to which the price belongs"}
   {:db/ident :price/trade-date
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "The date on which this price was paid for the commodity"}
   {:db/ident :price/value
    :db/valueType :db.type/bigdec
    :db/cardinality :db.cardinality/one
    :db/doc "The amount paid for one unit of the commodity"}
   
   ; Account
   {:db/ident :account/entity-id
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Identifies the entity to which the account belongs"}
   {:db/ident :account/commodity-id
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Identifies the commodity tracked by the account"}
   {:db/ident :account/parent-id
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Identifies the parent account to this account"}
   {:db/ident :account/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The name of the account"}
   {:db/ident :account/type
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/doc "The type of the account (asset, liability, equity, income, expense)"}
   {:db/ident :account/balance
    :db/valueType :db.type/bigdec
    :db/cardinality :db.cardinality/one
    :db/doc "The final balance of the account"}
   {:db/ident :account/first-trx-date
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "The date of the first transaction in the account"}
   {:db/ident :account/last-trx-date
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "The date of the last transaction in the account"}
   
   ; Transaction
   {:db/ident :transaction/entity-id
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Identifies the entity to which the transaction belongs"}
   {:db/ident :transaction/transaction-date
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "The date on which the transaction occurred"}
   {:db/ident :transaction/debit-account-id
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Identifies the account debited by this transaction"}
   {:db/ident :transaction/debit-index
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "The ordinal position of this transaction with the account being debited"}
   {:db/ident :transaction/debit-balance
    :db/valueType :db.type/bigdec
    :db/cardinality :db.cardinality/one
    :db/doc "The balance of the account being debited as a result of this transaction"}
   {:db/ident :transaction/credit-account-id
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Identifies the account credited by this transaction"}
   {:db/ident :transaction/credit-index
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "The ordinal position of this transaction with the account being credited"}
   {:db/ident :transaction/credit-balance
    :db/valueType :db.type/bigdec
    :db/cardinality :db.cardinality/one
    :db/doc "The balance of the account being credited as a result of this transaction"}
   {:db/ident :transaction/quantity
    :db/valueType :db.type/bigdec
    :db/cardinality :db.cardinality/one
    :db/doc "The quantity of the transaction"}
   {:db/ident :transaction/description
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "A description of the transaction"}
   {:db/ident :transaction/correlation-id
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/doc "An ID the indicates this transaction is part of a larger, compound transaction"}])

(def ^:private db-name "money")

(defmulti ->storable type)
(defmethod ->storable :default [x] x)
(defmethod ->storable LocalDate [d] (tc/to-long d))

(defn- init-conn
  [client]
  (try
    (d/connect client {:db-name db-name})
    (catch clojure.lang.ExceptionInfo e
      ; TODO: is there are better way that catching this exception?
      (let [d (ex-data e)]
        (if (= :cognitect.anomalies/not-found
               (:cognitect.anomalies/category d))
          (do (d/create-database client {:db-name db-name})
              (let [conn (d/connect client {:db-name db-name})]
                (d/transact conn {:tx-data schema
                                  :db-name db-name})
                conn))
          (throw (ex-info "Unable to connect to the database" d)))))))

(defmulti criteria->query
  (fn [m _opts]
    (mny/model-type m)))

(defn apply-id
  [query {:keys [id]}]
  (if id
    (-> query
        (update-in [:args] (fnil conj []) id)
        (update-in [:query :in] (fnil conj []) '?x))
    query))

(defmethod criteria->query :default
  [criteria opts]
  (let [m-type (or (mny/model-type criteria)
                   (:model-type opts))]
    (-> '{:query {:find [(pull ?x [*])]
                  :in [$]}
          :args []}
        (apply-id criteria)
        (dtl/apply-criteria (dissoc criteria :id)
                            :model-type m-type
                            :query-prefix [:query]
                            :coerce ->storable)
        (dtl/apply-options opts :model-type m-type))))

(defmulti before-save mny/model-type)
(defmulti after-read mny/model-type)

(defmethod before-save :default [m] m)
(defmethod after-read :default [m] m)

(defmulti ^:private prep-for-put
  (fn [m]
    (if (map? m)
      :map
      :vector)))

(defmethod prep-for-put :map
  [m]
  (let [[m* nils] (split-nils m)
        mt (mny/model-type m)]
    (cons (-> m*
              (mny/model-type mt)
              before-save
              (+id (comp str random-uuid))
              (rename-keys {:id :db/id})
              qualify-keys)
          (->> nils
               (remove #(nil? (-> m meta :original %)))
               (map #(vector :db/retract (:id m) (keyword (name mt) (name %))))))))

(def ^:private action-map
  {::mny/delete :db/retract
   ::mny/put    :db/add})

(defmethod prep-for-put :vector
  [[action {:keys [id] :as model}]]
  ; This is primarily for delete (retract), which seems to want
  ; the list form instead of the map form, so we retract each datom
  ; that is part of the map
  ; It's also necessary to nullify an existing value, such as an
  ; accounts :first-trx-date after deleting the only transaction
  (let [model-type (-> model mny/model-type name)
        op (action-map action)]
    (if (= :db/retract op)
      [[:db/retractEntity id]]
      (->> (-> model
               (dissoc :id)
               before-save)
           (map (fn [kv] ; qualify the key to make it a datomic attribute
                  (update-in kv [0] #(keyword model-type (name %)))))
           (reduce (fn [res [k v]]
                     (conj res [op id k v]))
                   [])))))

(defn- put*
  [models {:keys [conn]}]
  {:pre [(satisfies? Connection conn)]}
  (let [prepped (vec (mapcat prep-for-put models))
        {:keys [tempids]} (d/transact conn {:tx-data prepped})]
    (map #(or (tempids (:db/id %))
              (:db/id %))
         prepped)))

; It seems that after an entire entity has been retracted, the id
; can still be returned
(def ^:private naked-id?
  (every-pred map?
              #(= 1 (count %))
              #(= :db/id (first (keys %)))))

(defn- extract-ref-ids
  [m]
  (->> m
       (map #(update-in %
                        [1]
                        (fn [v]
                          (if (naked-id? v)
                            (:db/id v)
                            v))))
       (into {})))

(defn- select*
  [criteria options {:keys [conn]}]
  (let [query (-> criteria
                  (criteria->query options)
                  (update-in [:args]
                             prepend
                             (or (::db options)
                                 (d/db conn))))
        raw-result (d/q query)]
    (->> raw-result
         (map first)
         (remove naked-id?)
         (map (comp after-read
                    #(mny/model-type % criteria)
                    unqualify-keys
                    extract-ref-ids))
         (apply-sort options))))

(defmethod mny/reify-storage :datomic
  [config]
  (let [client (d/client config)
        conn (init-conn client)]
    (reify mny/Storage
      (put [_ models]       (put* models {:conn conn}))
      (select [_ crit opts] (select* crit opts {:conn conn}))
      (reset [_]            (d/delete-database client {:db-name db-name})))))
