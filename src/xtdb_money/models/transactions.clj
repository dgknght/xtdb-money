(ns xtdb-money.models.transactions
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.util :refer [->storable-date
                                     <-storable-date]]
            [xtdb-money.core :as mny]
            [xtdb-money.accounts :as a]
            [xtdb-money.models :as mdls]
            [xtdb-money.models.accounts :as acts])
  (:import org.joda.time.LocalDate))

(s/def ::transaction-date (partial instance? LocalDate))
(s/def ::debit-account-id uuid?)
(s/def ::credit-account-id uuid?)
(s/def ::amount (s/and decimal?
                       #(< 0M %)))
(s/def ::debit-index integer?)
(s/def ::debit-balance decimal?)
(s/def ::credit-index integer?)
(s/def ::credit-balance decimal?)
(s/def ::transaction (s/keys :req-un [::mdls/entity-id
                                      ::transaction-date
                                      ::credit-account-id
                                      ::debit-account-id
                                      ::amount]
                             :opt-un [::mdls/id
                                      ::debit-index
                                      ::debit-balance
                                      ::credit-index
                                      ::credit-balance]))

(s/def ::start-date (partial instance? LocalDate))
(s/def ::end-date (partial instance? LocalDate))

(defn- criteria-dispatch
  [& args]
  (let [{:keys [entity-id account-id id]} (last args)]
    (cond
      id :id
      entity-id :entity-id
      account-id :account-id)))

(defmulti ^:private criteria criteria-dispatch)
(defmethod criteria :id
  [_]
  (s/keys :req-un [::mdls/id]))
(defmethod criteria :account-id
  [_]
  (s/keys :req-un [::account-id
                   ::start-date
                   ::end-date]))
(defmethod criteria :entity-id
  [_]
  (s/keys :req-un [::entity-id
                   ::start-date
                   ::end-date]))

(s/def ::criteria (s/multi-spec criteria :account-id))

(s/def ::offset integer?)
(s/def ::limit integer?)
(s/def ::options (s/keys :opt-un [::offset ::limit]))

(defmulti ^:private apply-criteria criteria-dispatch)

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

(defn- apply-options
  [query {:keys [limit offset]}]
  (cond-> query
    limit (assoc :limit limit)
    offset (assoc :offset offset)))

(defn- after-read
  [trx]
  (-> trx
      (with-meta {:model-type :transaction})
      (update-in [:transaction-date] <-storable-date)))

(def ^:private base-query
  (mny/query-map :transaction
                 entity-id
                 transaction-date
                 debit-account-id
                 debit-index
                 debit-balance
                 credit-account-id
                 credit-index
                 credit-balance
                 amount))

(defn select
  ([] (select {}))
  ([criteria] (select criteria {}))
  ([criteria options]
   {:pre [(s/valid? ::criteria criteria)
          (s/valid? ::options options)]}
   (let [[query param] (-> base-query
                           (assoc :order-by [['transaction-date :asc]])
                           (apply-options options)
                           (apply-criteria criteria))]
     (map after-read (mny/select query param)))))

(defprotocol UnilateralTransaction
  (account [this] "Returns the account for the transaction")
  (account-id [this] "Retrieves the ID for the account to which the transaction belongs")
  (amount [this] "The polarized amount of the transaction")
  (index [this] "Ordinal position of this transaction within the account")
  (set-index [this index] "Set the ordinal position")
  (balance [this] "Balance of the account as of this transaction")
  (set-balance [this balance] "Set the balance")
  (transaction-date [this] "The date of the transaction")
  (bilateral [this] "Returns the underlaying bilateral transaction"))

(def ^{:private true :dynamic true} *accounts* nil)

(defmacro with-accounts
  "Provides contextual access to all of the accounts in the entity"
  [model & body]
  `(binding [*accounts* (->> (acts/select (select-keys ~model [:entity-id]))
                             (map (juxt :id identity))
                             (into {}))]
     ~@body))

(defn- split-keys
  [action]
  (let [a (name action)]
    {:index (keyword (str a "-index"))
     :balance (keyword (str a "-balance"))
     :account-id (keyword (str a "-account-id"))}))

(defn- split
  "Accepts a storable transaction and returns reified UnilateralTransaction instances,
  one for debit and one for credit"
  ([trx]
   {:pre [trx]}

   {:debit (split trx :debit)
    :credit (split trx :credit)})
  ([trx action]
   {:pre [*accounts*]}
   (let [k (split-keys action)
         account-id (get-in trx [(:account-id k)])
         account (get-in *accounts* [account-id])]
     (reify UnilateralTransaction
       (account-id [_] account-id)
       (account [_] account)
       (amount [_]
         (a/polarize {:amount (:amount trx)
                      :account account
                      :action action}))
       (index [_]
         (get-in trx [(k :index)]))
       (set-index [_ index]
         (get-in (-> trx
                     (assoc (k :index)
                            index)
                     (split))
                 [action]))
       (balance [_] (get-in trx [(k :balance)]))
       (set-balance [_ balance]
         (get-in (-> trx
                     (assoc (k :balance)
                            balance)
                     (split))
                 [action]))
       (transaction-date [_] (:transaction-date trx))
       (bilateral [_] trx)))))

(defn- split-and-filter
  [account trxs]
  (->> trxs
       (mapcat (comp vals split))
       (filter #(= (:id account)
                   (account-id %)))))

(defn select-by-account
  [account start-date end-date]
  (with-accounts account
    (split-and-filter account
                      (select {:account-id (:id account)
                               :start-date start-date
                               :end-date end-date}))))

(defn- precedent
  [{:keys [transaction-date]} account]
  (let [query (-> base-query
                  (update-in [:where] conj '(or [(= debit-account-id account-id)]
                                                [(= credit-account-id account-id)]))
                  (update-in [:where] conj ['(<= transaction-date d)])
                  (assoc :order-by [['transaction-date :desc]] ; TODO: put back index
                         :limit 1
                         :in '[[account-id d]]))]
    (->> (mny/select query
                     [(:id account)
                      (->storable-date transaction-date)])
         (map after-read)
         (split-and-filter account)
         first)))

(defn- subsequents
  [{:keys [transaction-date]} account]
  (let [query (-> base-query
                  (update-in [:where] conj ['(<= d transaction-date)])
                  (update-in [:where] conj ['(or (== account-id credit-account-id)
                                                 (== account-id debit-account-id))])
                  (assoc :order-by [['transaction-date :asc]] ; TODO: put back the index
                         :in '[[account-id d]]))]
    (->> (mny/select query
                     [(:id account)
                      (->storable-date transaction-date)])
         (map after-read)
         (split-and-filter account))))

(defn find
  [id]
  (first (select {:id id})))

(defn- ensure-model-type
  [trx]
  (case (-> trx meta :model-type)
    :transaction trx
    nil (vary-meta trx assoc :model-type :transaction)
    (throw (ex-info "Unexpected model type" {:model trx
                                             :meta (meta trx)}))))

(defn- before-save
  [trx]
  (-> trx
      (ensure-model-type)
      (update-in [:transaction-date] ->storable-date)))

(defn- apply-index
  [{:keys [last-index last-balance] :as result} trx]
  {:pre [result trx]}

  (let [index (+ 1 last-index)
        balance (+ last-balance
                   (amount trx))]
    (-> result
        (assoc :last-index index
               :last-balance balance)
        (update-in [:out] conj (-> trx
                                   (set-index index)
                                   (set-balance balance)
                                   bilateral)))))

; To propagate, get the transaction immediately preceding the one being put
; this is the starting point for the index and balance updates.
; continue updating the chain until a transaction is unmodified
; Note that if this is an update, we'll also need to address anything changed
; by removing it from its old location
;
; This need to be done for both the debit and the credit account.
; Care must be taken to ensure that any transactions that are in
; both the debit and credit chains are only updated once
(defn- propagate*
  [trx action account]
  (let [prev (precedent trx account)]
    (->> [(get-in (split trx) [action])]
         (reduce apply-index
                 {:last-index (if prev (index prev) 0)
                  :last-balance (if prev (balance prev) 0M)})
         :out
         (into []))))

(defn- propagate
  [{:keys [debit-account-id credit-account-id amount entity-id] :as trx}]
  (let [debit-account (acts/find debit-account-id)
        credit-account (acts/find credit-account-id)
        _ (assert (= entity-id
                     (:entity-id debit-account)
                     (:entity-id credit-account))
                  "All accounts in a transaction must belong to the same entity as the transaction")
        debit-side (propagate* trx :debit debit-account)
        credit-side (propagate* (first debit-side) :credit credit-account)]

    ; The calling fn expects the transaction to be in the first position
    (concat 
      credit-side ; TODO: Remove any duplicates here
      (rest debit-side) ; this 1st one here should be the first one in credit-side also
      ; TODO: the accounts should only be updated if the update chain makes it
      ; to the last transaction for the account
      [(update-in debit-account [:balance] + (a/polarize amount :debit debit-account))
       (update-in credit-account [:balance] + (a/polarize amount :credit credit-account))])))

(defn put
  [trx]
  {:pre [(s/valid? ::transaction trx)]}

  (with-accounts trx
    (-> (apply mny/put (propagate (before-save trx)))
        first
        find)))
