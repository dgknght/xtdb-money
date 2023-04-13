(ns xtdb-money.test-context
  (:require [xtdb-money.models.accounts :as acts]
            [xtdb-money.models.transactions :as trxs]))

(defonce ^:dynamic *context* nil)

(defn find-account
  ([account-name] (find-account account-name *context*))
  ([account-name {:keys [accounts]}]
   (->> accounts
        (filter #(= account-name (:name %)))
        first)))

(defn- resolve-account
  ([model ctx] (resolve-account model ctx :account-id))
  ([model ctx k]
   (update-in model [k] find-account ctx)))

(defn- realize-account
  [account _ctx]
  (acts/put account))

(defn- realize-accounts
  [ctx]
  (update-in ctx [:accounts] (fn [accounts]
                               (map #(realize-account % ctx)
                                    accounts))))

(defn- realize-transaction
  [trx ctx]
  (-> trx
      (resolve-account ctx :debit-account-id)
      (resolve-account ctx :credit-account-id)
      (trxs/put)))

(defn- realize-transactions
  [ctx]
  (update-in ctx [:transactions] (fn [transactions]
                                   (map #(realize-transaction % ctx)
                                        transactions))))

(defn realize
  [ctx]
  (-> ctx
      realize-accounts
      realize-transactions))

(defmacro with-context
  [ctx & body]
  `(binding [*context* (realize ~ctx)]
    ~@body))
