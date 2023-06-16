(ns xtdb-money.models.datomic.transactions
  (:require [xtdb-money.datomic :as d]
            [cljs.core :as c]))

(defn- apply-id
  [query {:keys [id]}]
  (if id
    (-> query
        (update-in [:query :in] conj '?t)
        (update-in [:args] conj id))
    query))

(defmulti ^:private transaction-date-clauses
  (fn [criterion]
    (if (vector? criterion)
      (if (#{:and :or} (first criterion))
        :compound
        :comparison)
      :simple)))

(defmethod transaction-date-clauses :simple
  [_]
  '[[?t :transaction/transaction-date ?d]])

(defmethod transaction-date-clauses :comparison
  [[oper _]]
  [[(symbol oper] '?d])

(defn- apply-transaction-date
  [query {:keys [transaction-date]}]

  (clojure.pprint/pprint {::apply-transaction-date transaction-date})

  (-> query
      (update-in [:query :in] conj '?d)
      (update-in [:args] conj transaction-date)
      (update-in [:query :where] concat (transaction-date-clauses transaction-date) '[?t :transaction/transaction-date ?d])))

(defmethod d/criteria->query :transaction
  [criteria _options]
  (-> '{:find [(pull ?t [*])]
        :where []
        :args []}
      (apply-id criteria)
      (apply-transaction-date criteria)))


