(ns xtdb-money.transactions
  (:require #?(:clj [clojure.spec.alpha :as s]
               :cljs [cljs.spec.alpha :as s])
            [xtdb-money.util :refer [local-date?]]))

#?(:cljs (defn- decimal?
            [_]
            (throw (js/Errors. "Not implemented"))))

(s/def ::transaction-date local-date?)
(s/def ::description string?)
(s/def ::action #{:credit :debit})
(s/def ::account-id (s/or :integer integer?
                          :uuid uuid?))
(s/def ::quantity (s/and decimal?
                       #(<= 0M %)))
(s/def ::item (s/keys :req-un [::action
                               ::account-id
                               ::quantity]))
(s/def ::items (s/coll-of ::item))

(defn- credits-equal-debits?
  [{:keys [items]}]
  (->> items
       (group-by :action)
       (map (comp #(reduce + 0M %)
                  #(update-in % [1] :quantity)))))

(s/def ::complex-transaction (s/and (s/keys :req-un [::transaction-date
                                              ::description
                                              ::items])
                                    credits-equal-debits?))

(defn- arrange-items
  [{:keys [items]}]
  (->> items
       (group-by :action)
       (map #(update-in % [1] (fn [i]
                                (->> i
                                     (sort-by :quantity >)
                                     (into [])))))
       (into {})))

(defn- subtract-quantity
  [[item :as items] quantity]
  (if (= quantity (:quantity item))
    (vec (rest items))
    (update-in items [0 :quantity] - quantity)))

(defn- account-id-key
  [action]
  {:pre [(#{:debit :credit} action)]}
  (-> action
      name
      (str "-account-id")
      keyword))

(defn- extract
  "Take one or both trx items from debit and credit and return
  a tuple containing the basic transaction and the remaining
  transaction items"
  [sides complex whole part]
  (let [quantity (get-in sides [whole 0 :quantity])]
    [(-> complex
         (select-keys [:description
                       :transaction-date])
         (assoc :quantity quantity
                (account-id-key part) (get-in sides [part 0 :account-id])
                (account-id-key whole) (get-in sides [whole 0 :account-id])))
     (-> sides
         (update-in [part] subtract-quantity quantity)
         (update-in [whole] (comp vec rest)))]))

(defn- pair
  "Take the available transaction items and yield a simple transaction
  and the remaining transaction items"
  [{[d] :debit [c] :credit :as sides} complex]
  (if (< (:quantity d) (:quantity c))
    (extract sides complex :debit :credit)
    (extract sides complex :credit :debit)))

(defn split
  "Given a complex transaction, yields a sequence of simple transactions"
  [complex]
  {:pre [(s/valid? ::complex-transaction complex)]}

  (loop [sides (arrange-items complex) result #{}]
    (if (seq (:debit sides))
      (let [[trx new-sides] (pair sides complex)]
        (recur new-sides (conj result trx)))
      result)))

(defn join
  "Given a list of simple transactions, yield a complex transaction"
  [trxs]
  ; We're assuming these have already been correctly identified as members of the
  ; same complex transaction
  (let [sides (reduce (fn [acc {:keys [debit-account-id quantity credit-account-id]}]
                        (-> acc
                            (update-in [:debit debit-account-id] (fnil + 0M) quantity)
                            (update-in [:credit credit-account-id] (fnil + 0M) quantity)))
                      {:debit {} :credit {}}
                      trxs)]
    (-> (first trxs)
        (select-keys [:description :transaction-date])
        (assoc :items (->> sides
                           (mapcat (fn [[action accounts]]
                                     (map (fn [[account-id quantity]]
                                            {:action action
                                             :quantity quantity
                                             :account-id account-id})
                                          accounts)))
                           (into #{}))))))
