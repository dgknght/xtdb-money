(ns xtdb-money.test-context
  (:require [xtdb-money.models.entities :as ents]
            [xtdb-money.models.accounts :as acts]
            [xtdb-money.models.transactions :as trxs]))

(defonce ^:dynamic *context* nil)

(def basic-context
  {:entities [{:name "Personal"}]
   :accounts [{:entity-id "Personal"
               :name "Checking"
               :type :asset}
              {:entity-id "Personal"
               :name "Credit Card"
               :type :liability}
              {:entity-id "Personal"
               :name "Salary"
               :type :income}
              {:entity-id "Personal"
               :name "Rent"
               :type :expense}
              {:entity-id "Personal"
               :name "Groceries"
               :type :expense}]})

(defn- find-model
  [coll k v]
  (->> coll
       (filter #(= v (get-in % [k])))
       first))

(defn find-entity
  ([entity-name] (find-entity entity-name *context*))
  ([entity-name {:keys [entities]}]
   (find-model entities :name entity-name)))

(defn find-account
  ([account-name] (find-account account-name *context*))
  ([account-name {:keys [accounts]}]
   (find-model accounts :name account-name)))

(defn- resolve-entity
  ([model ctx] (resolve-entity model ctx :entity-id))
  ([model ctx k]
   (update-in model [k] (comp :id find-entity) ctx)))

(defn- throw-on-failure
  [m]
  (if m m (throw (RuntimeException. "Unable to create the model"))))

(defn- realize-entity
  [entity _ctx]
  (ents/put entity))

(defn- realize-entities
  [ctx]
  (update-in ctx [:entities] (fn [entities]
                               (mapv (comp throw-on-failure
                                           #(realize-entity % ctx))
                                     entities))))

(defn- resolve-account
  ([model ctx] (resolve-account model ctx :account-id))
  ([model ctx k]
   (update-in model [k] (comp :id find-account) ctx)))

(defn- realize-account
  [account ctx]
  (-> account
      (resolve-entity ctx)
      (acts/put)))

(defn- realize-accounts
  [ctx]
  (update-in ctx [:accounts] (fn [accounts]
                               (mapv (comp throw-on-failure
                                           #(realize-account % ctx))
                                     accounts))))

(defn- realize-transaction
  [trx ctx]
  (-> trx
      (resolve-entity ctx)
      (resolve-account ctx :debit-account-id)
      (resolve-account ctx :credit-account-id)
      (trxs/put)))

(defn- realize-transactions
  [ctx]
  (update-in ctx [:transactions] (fn [transactions]
                                   (mapv (comp throw-on-failure
                                               #(realize-transaction % ctx))
                                         transactions))))

(defn realize
  [ctx]
  (-> ctx
      realize-entities
      realize-accounts
      realize-transactions))

(defmacro with-context
  [& [a1 :as args]]
  (let [[ctx & body] (if (symbol? a1)
                       args
                       (cons basic-context args))]
    `(binding [*context* (realize ~ctx)]
       ~@body)))
