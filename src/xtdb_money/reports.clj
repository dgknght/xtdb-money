(ns xtdb-money.reports
  (:require [clojure.string :as string]
            [xtdb-money.models.transactions :as trxs]
            [xtdb-money.models.accounts :as acts]
            [xtdb-money.accounts :refer [polarize]]))

(defn- lookup-accounts
  "Given a map of accounts with :id as keys and a list of transactions
  as read from the database, lookup the accounts and append them
  to each transaction"
  [accounts transactions]
  (map #(assoc %
               :debit-account (accounts (:debit-account-id %))
               :credit-account (accounts (:credit-account-id %)))
       transactions))

(defn- split-actions
  "Given a list of transactions with debit and credit accounts
  resolved, for each transaction, yield one credit and one debit
  transaction with polarized amounts"
  [transactions]
  (mapcat (fn [{:keys [credit-account debit-account amount]}]
            [{:account credit-account
              :amount (polarize amount :credit credit-account)}
             {:account debit-account
              :amount (polarize amount :debig debit-account)}])
          transactions))

(defn- aggregate-by-account
  "Given a list of simplified transactions, return a list
  of tuples with the account in the first position and
  the total for that account in the second"
  [transactions]
  (->> transactions
       (group-by :account)
       (map #(update-in %
                        [1]
                        (fn [ts]
                          (->> ts
                               (map :amount)
                               (reduce + 0M)))))))

(defn- aggregate
  "Given a tuple with an account type in the first position
  and a list of simplified transactions in the second, return
  a type with the same first position and the same simplified
  transactions, but rolled up by account"
  [group]
  (update-in group [1] aggregate-by-account))

(defn- summarized-transactions
  "Yields a map where the keys are account types and the values are
  a collection of tuples with an account in the first position and
  the total for the account in the second."
  [entity-id]
  (let [accounts (->> (acts/select {:entity-id entity-id})
                      (map (juxt :id identity))
                      (into {}))]
    (->> (trxs/select {:entity-id entity-id})
         (lookup-accounts accounts)
         (split-actions)
         (group-by #(get-in % [:account :type]))
         (map aggregate)
         (into {}))))

(defn- ->rows
  "Given an account type and an aggregated list of simplified
  transactions, return report rows."
  [type transactions]
  (let [by-type (transactions type)]
      (cons {:style :header
             :label (string/capitalize (name type))
             :value (->> by-type
                         (map second)
                         (reduce + 0M))}
            (map (fn [[account total]]
                   {:style :data
                    :depth 0
                    :value total
                    :label (:name account)})
                 by-type))))

(defn- extract-totals
  "Given a transaction summary map and a list of types,
  return the totals for the types."
  [transactions & types]
  (->> types
       (map #(->> (transactions %)
                  (map second)
                  (reduce + 0M)))))

(defn- append-pseudo-account
  "Given a transaction summary map and a name and currency value,
  return the transaction map with the specified name and value
  appended to the specified account type list"
  [transactions type name value]
  (update-in transactions
             [type]
             (fnil
               #(conj % [{:name name} value])
               [])))

(defn balance-sheet
  [entity-id]
  (let [transactions (summarized-transactions entity-id)
        [inc-total exp-total] (extract-totals transactions
                                              :income
                                              :expense)
        prepped (append-pseudo-account
                  transactions
                  :equity
                  "Retained Earnings"
                  (- inc-total exp-total))]
    (->> [:asset :liability :equity]
         (mapcat #(->rows % prepped)))))

(defn income-statement
  [entity-id]
  (let [transactions (summarized-transactions entity-id)]
    (->> [:income :expense]
         (mapcat #(->rows % transactions)))))
