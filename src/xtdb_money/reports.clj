(ns xtdb-money.reports
  (:require [clojure.string :as string]
            [xtdb-money.models.transactions :as trxs]
            [xtdb-money.models.accounts :as acts]
            [xtdb-money.accounts :refer [polarize]]))

(defn- summarized-transactions
  "Yields a map where the keys are account types and the values are
  a collection of tuples with an account in the first position and
  the total for the account in the second."
  [entity-id]
  (let [accounts (->> (acts/select {:entity-id entity-id})
                      (map (juxt :id identity))
                      (into {}))]
    (->> (trxs/select {:entity-id entity-id})
         (map #(assoc %
                      :debit-account (accounts (:debit-account-id %))
                      :credit-account (accounts (:credit-account-id %))))
         (mapcat (fn [{:keys [credit-account debit-account amount]}]
                   [{:account credit-account
                     :amount (polarize amount :credit credit-account)}
                    {:account debit-account
                     :amount (polarize amount :debig debit-account)}]))
         (group-by #(get-in % [:account :type]))
         (map (fn [group]
                (update-in group [1] (fn [transactions]
                                       (->> transactions
                                            (group-by :account)
                                            (map (fn [[account ts]]
                                                   [account
                                                    (->> ts
                                                         (map :amount)
                                                         (reduce + 0M))])))))))
         (into {}))))

(defn- group->rows
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

(defn balance-sheet
  [entity-id]
  (let [transactions (summarized-transactions entity-id)
        [inc-total exp-total] (->> [:income :expense]
                                   (map #(->> (transactions %)
                                              (map second)
                                              (reduce + 0M))))
        prepped (update-in transactions
                           [:equity]
                           (fnil
                             (fn [group]
                               (conj group [{:name "Retained Earnings"}
                                            (- inc-total
                                               exp-total)]))
                             []))]
    (->> [:asset :liability :equity]
         (mapcat #(group->rows % prepped)))))

(defn income-statement
  [entity-id]
  (let [transactions (summarized-transactions entity-id)]
    (->> [:income :expense]
         (mapcat #(group->rows % transactions)))))
