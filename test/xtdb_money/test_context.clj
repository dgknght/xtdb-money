(ns xtdb-money.test-context
  (:require [clojure.pprint :refer [pprint]]
            [xtdb-money.models.entities :as ents]
            [xtdb-money.models.commodities :as cty]
            [xtdb-money.models.accounts :as acts]
            [xtdb-money.models.transactions :as trxs]))

(defonce ^:dynamic *context* nil)

(def basic-context
  {:entities [{:name "Personal"}]
   :commodities [{:entity-id "Personal"
                  :type :currency
                  :name "United States Dollar"
                  :symbol "USD"}]
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
               :type :expense}
              {:entity-id "Personal"
               :name "Dining"
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

(defn find-commodity
  ([sym] (find-commodity sym *context*))
  ([sym {:keys [commodities]}]
   (find-model commodities :symbol sym)))

(defn find-account
  ([account-name] (find-account account-name *context*))
  ([account-name {:keys [accounts]}]
   (find-model accounts :name account-name)))

(defn find-transaction
  ([trx-date description]
   (find-transaction trx-date description *context*))
  ([trx-date description {:keys [transactions]}]
   (->> transactions
        (filter #(and (= trx-date (:transaction-date %))
                      (= description (:description %))))
        first)))

(defn- resolve-entity
  ([model ctx] (resolve-entity model ctx :entity-id))
  ([model ctx k]
   (update-in model [k] (comp :id find-entity) ctx)))

(defn- resolve-commodity
  ([model ctx] (resolve-commodity model ctx :commodity-id))
  ([model ctx k]
   (update-in model
              [k]
              (fn [id]
                (:id (or (find-commodity id ctx)
                         (when-let [entity-id (:entity-id model)]
                           (->> (:commodities ctx)
                                (filter #(= (:entity-id %)
                                            entity-id))
                                first))))))))

(defn- put-with
  [m f]
  (or (f m)
      (pprint {::unable-to-create m})))

(defn- throw-on-failure
  [model-type]
  (fn [m]
    (or m
        (throw (RuntimeException. (format "Unable to create the %s" model-type))))))

(defn- realize-entity
  [entity _ctx]
  (ents/put entity))

(defn- realize-entities
  [ctx]
  (update-in ctx [:entities] (fn [entities]
                               (mapv (comp (throw-on-failure "entity")
                                           #(realize-entity % ctx))
                                     entities))))

(defn- realize-commodity
  [commodity ctx]
  (-> commodity
      (resolve-entity ctx)
      (cty/put)))

(defn- realize-commodities
  [ctx]
  (update-in ctx [:commodities] (fn [commodities]
                               (mapv (comp (throw-on-failure "commodity")
                                           #(realize-commodity % ctx))
                                     commodities))))

(defn- resolve-account
  ([model ctx] (resolve-account model ctx :account-id))
  ([model ctx k]
   (update-in model [k] (comp :id find-account) ctx)))

(defn- realize-account
  [account ctx by-name]
  (-> account
      (resolve-entity ctx)
      (resolve-commodity ctx) ; must resolve the entity first
      (update-in [:parent-id] (comp :id by-name))
      (acts/put)))

(defn- realize-accounts
  [ctx]
  (update-in ctx
             [:accounts]
             (fn [accounts]
               (:created (reduce (fn [{:keys [by-name] :as r} input]
                                   (let [account (realize-account input ctx by-name)]
                                     (-> r
                                         (update-in [:created] conj account)
                                         (assoc-in [:by-name (:name account)] account))))
                                 {:created []
                                  :by-name {}}
                                 accounts)))))

(defn- realize-transaction
  [trx ctx]
  (-> trx
      (resolve-entity ctx)
      (resolve-account ctx :debit-account-id)
      (resolve-account ctx :credit-account-id)
      (put-with trxs/put)))

(defn- realize-transactions
  [ctx]
  (update-in ctx [:transactions] (fn [transactions]
                                   (mapv (comp (throw-on-failure "transaction")
                                               #(realize-transaction % ctx))
                                         transactions))))

(defn realize
  [ctx]
  (-> ctx
      realize-entities
      realize-commodities
      realize-accounts
      realize-transactions))

(defmacro with-context
  [& [a1 :as args]]
  (let [[ctx & body] (if (symbol? a1)
                       args
                       (cons basic-context args))]
    `(binding [*context* (realize ~ctx)]
       ~@body)))
