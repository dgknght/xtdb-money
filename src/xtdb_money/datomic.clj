(ns xtdb-money.datomic
  (:require [clojure.set :refer [rename-keys]]
            [datomic.client.api :as d]
            [xtdb-money.util :refer [qualify-keys]]
            [xtdb-money.core :as mny]))

(def schema
  [
   ; Entity
   {:db/ident :entity/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The name of the entity"}
   
   ; Account
   {:db/ident :account/entity-id
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Identifies the entity to which the account belongs"}
   {:db/ident :account/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The name of the account"}
   {:db/ident :account/type
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The type of the account (asset, liability, equity, income, expense)"}
   {:db/ident :account/balance
    :db/valueType :db.type/bigdec
    :db/cardinality :db.cardinality/one
    :db/doc "The final balance of the account"}
   {:db/ident :account/first-trx-date
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The date of the first transaction in the account"}
   {:db/ident :account/last-trx-date
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The date of the last transaction in the account"}])

(def ^:private db-name "money")


#_(defmethod mny/start :datomic
  [config]
  (let [cl (client config)
        _ (d/create-database cl {:db-name db-name}) ; TODO: should this be run every time? only once?
        cn (d/connect cl {:db-name db-name})]
    (reset! conn cn)
    (d/transact cn {:tx-data schema
                    :db-name db-name}))) ; TODO: should this be run every time? only once?

#_(defmethod mny/stop :datomic [_]
  (reset! conn nil))

#_(defmethod mny/reset-db :datomic
  [config]
  (d/delete-database (client config) {:db-name db-name}))

#_(defn- transact
  [models]
  (d/transact (d/connect (client cfg)
                         {:db-name db-name})
              {:tx-data (map (comp #(rename-keys % {:entity/id :db/id}) ; TODO: unkludge this
                                   qualify-keys)
                             models)}))

#_(defn query
  [cfg q & args]
  (d/q {:query q
        :args (cons (d/db
                      (d/connect (client cfg)
                                 {:db-name db-name}))
                    args)}))

#_(defn index-pull
  [q]
  (d/index-pull (db) q))

(defn- prepend-db
  [coll db-fn]

  (clojure.pprint/pprint {::prepend-db coll})

  (cons (db-fn coll))
  #_(if (instance? com.datomic.Db (first coll))
    coll
    (cons (db-fn) coll)))

(defn- init-conn
  [client]
  (try
    (d/connect client {:db-name db-name})
    (catch clojure.lang.ExceptionInfo e
      ; TODO: is there are better way that catching this exception?
      (if (= :cognitect.anomalies/not-found
             (:cognitect.anomalies/category (ex-data e)))
        (do
          (d/create-database client {:db-name db-name})
          (let [conn (d/connect client {:db-name db-name})]
            (d/transact conn {:tx-data schema
                              :db-name db-name})))
        :enable-to-connect))))

(defmethod mny/reify-storage :datomic
  [config]
  (let [client (d/client config)
        conn (init-conn client)]

    (reify mny/Storage
      (put [_ models]
        (let [prepped (map (comp #(rename-keys % {:entity/id :db/id})
                                 qualify-keys)
                           models)
              result (d/transact conn {:tx-data prepped})]
          (clojure.pprint/pprint {::result result}))
        [])
      (select [_ query args]
        (d/q {:query query
              :args (prepend-db args #(d/db conn))}))
      (reset [_]
        (d/delete-database client {:db-name db-name})))))
