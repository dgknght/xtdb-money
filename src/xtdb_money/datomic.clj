(ns xtdb-money.datomic
  (:require [clojure.set :refer [rename-keys]]
            [config.core :refer [env]]
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

(defn- client
  [config]
  (d/client config))

(def ^:private conn (atom nil))

(defn- db [] (d/db @conn))

(defmethod mny/start :datomic
  [config]
  (let [cl (client config)
        _ (d/create-database cl {:db-name db-name}) ; TODO: should this be run every time? only once?
        cn (d/connect cl {:db-name db-name})]
    (reset! conn cn)
    (d/transact cn {:tx-data schema
                    :db-name db-name}))) ; TODO: should this be run every time? only once?

(defmethod mny/stop :datomic [_]
  (reset! conn nil))

(defmethod mny/reset-db :datomic
  [config]
  (d/delete-database (client config) {:db-name db-name}))

(defn transact
  [cfg models]
  (d/transact (d/connect (client cfg)
                         {:db-name db-name})
              {:tx-data (map (comp #(rename-keys % {:entity/id :db/id}) ; TODO: unkludge this
                                   qualify-keys)
                             models)}))

(defn query
  [cfg q & args]
  (d/q {:query q
        :args (cons (d/db
                      (d/connect (client cfg)
                                 {:db-name db-name}))
                    args)}))

(defn index-pull
  [q]
  (d/index-pull (db) q))
