(ns xtdb-money.models.xtdb.transactions
  (:require [xtdb-money.util :refer [->storable-date
                                     local-date?]]
            [xtdb-money.xtdb :as x]
            [xtdb-money.models.transactions :as trxs]))

(defmulti ^:private apply-criteria trxs/criteria-dispatch)

(defmethod apply-criteria :id
  [query {:keys [id]}]
  [(assoc query :in '[id])
   id])

(defn- apply-date-range
  [query]
  (-> query
      (update-in [:where] conj '[(<= start-date transaction-date)])
      (update-in [:where] conj '[(< transaction-date end-date)])))

(defmethod apply-criteria :account-id
  [query {:keys [account-id] :as criteria}]
  [(-> query
       (assoc :in '[[account-id start-date end-date]])
       (apply-date-range))
   (cons account-id (map (comp ->storable-date criteria)
                         [:start-date :end-date]))])

(defmethod apply-criteria :entity-id
  [query {:keys [entity-id] :as criteria}]
  [(-> query
       (assoc :in '[[entity-id start-date end-date]])
       (apply-date-range))
   (cons entity-id (map (comp ->storable-date criteria)
                        [:start-date :end-date]))])

(def ^:private base-query
  (x/query-map :transaction
               entity-id
               correlation-id
               transaction-date
               description
               debit-account-id
               debit-index
               debit-balance
               credit-account-id
               credit-index
               credit-balance
               amount))

(defn- apply-options
  [query {:keys [limit offset]}]
  (cond-> query
    limit (assoc :limit limit)
    offset (assoc :offset offset)))

(defmethod trxs/query :xtdb
  [criteria options]
  (let [[query param] (-> base-query
                          (assoc :order-by [['transaction-date :asc]])
                          (apply-options options)
                          (apply-criteria criteria))]
    (x/select query param)))

(defmethod trxs/submit :xtdb
  [& models]
  (apply x/submit models))

(defn- apply-account-id
  [query]
  (-> query
      (update-in [:where]
                 conj
                 '(or [id :transaction/debit-account-id account-id]
                      [id :transaction/credit-account-id account-id]))))

(defmethod trxs/preceding :xtdb
  [transaction-date account-id limit]
  (let [query (-> base-query
                  (apply-account-id)
                  (update-in [:where]
                             conj
                             ['(< transaction-date d)])
                  (assoc :order-by [['transaction-date :desc]] ; TODO: put back index
                         :limit limit
                         :in '[[account-id d]]))]
    (x/select query
              [account-id
               (->storable-date transaction-date)])))

(defmethod trxs/subsequents* :xtdb
  [transaction-date account-id]
  {:pre [(local-date? transaction-date)
         (uuid? account-id)]}

  (let [query (-> base-query
                  (apply-account-id)
                  (update-in [:where]
                             conj
                             ['(<= d transaction-date)])
                  (assoc :order-by [['transaction-date :asc]] ; TODO: put back the index
                         :in '[[account-id d]]))]
    (x/select query
              [account-id
               (->storable-date transaction-date)])))
