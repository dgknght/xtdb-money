(ns xtdb-money.models.transactions
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [clojure.pprint :as pp]
            [xtdb.api :as xt]
            [clj-time.core :as t]
            [clj-time.format :refer [formatters unparse]]
            [clj-time.coerce :refer [to-date-time]]
            [xtdb-money.util :refer [->storable-date
                                     <-storable-date
                                     local-date?
                                     ->id]]
            [xtdb-money.core :as mny]
            [xtdb-money.accounts :as a]
            [xtdb-money.models :as mdls]
            [xtdb-money.models.accounts :as acts])
  (:import org.joda.time.LocalDate))

(s/def ::transaction-date local-date?)
(s/def ::description string?)
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
                                      ::description
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

(def ^{:private true :dynamic true} *accounts* nil)

(defmacro with-accounts
  "Provides contextual access to all of the accounts in the entity"
  [model & body]
  `(binding [*accounts* (->> (acts/select (select-keys ~model [:entity-id]))
                             (map (juxt :id identity))
                             (into {}))]
     ~@body))

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
                 description
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
  (other-account [this] "Returns the account on the other side of the transaction")
  (amount [this] "The polarized amount of the transaction")
  (index [this] "Ordinal position of this transaction within the account")
  (set-index [this index] "Set the ordinal position")
  (balance [this] "Balance of the account as of this transaction")
  (set-balance [this balance] "Set the balance")
  (transaction-date [this] "The date of the transaction")
  (description [this] "The description of the transaction")
  (bilateral [this] "Returns the underlaying bilateral transaction"))

(declare split)

(defrecord CreditSide [trx accounts]
  UnilateralTransaction
  (account [_] (-> trx :credit-account-id accounts))
  (account-id [_] (:credit-account-id trx))
  (other-account [_] (-> trx :debit-account-id accounts))
  (amount [this]
    (a/polarize {:amount (:amount trx)
                 :account (account this)
                 :action :credit}))
  (index [_] (:credit-index trx))
  (set-index [_ index]
    (-> trx
        (assoc :credit-index
               index)
        (split)
        :credit))
  (balance [_] (:credit-balance trx))
  (set-balance [_ balance]
    (-> trx
        (assoc :credit-balance balance)
        (split)
        :credit))
  (transaction-date [_] (:transaction-date trx))
  (description [_] (:description trx))
  (bilateral [_] trx))

(defrecord DebitSide [trx accounts]
  UnilateralTransaction
  (account [_] (-> trx :debit-account-id accounts))
  (account-id [_] (:debit-account-id trx))
  (other-account [_] (-> trx :credit-account-id accounts))
  (amount [this]
    (a/polarize {:amount (:amount trx)
                 :account (account this)
                 :action :debit}))
  (index [_] (:debit-index trx))
  (set-index [_ index]
    (-> trx
        (assoc :debit-index
               index)
        (split)
        :debit))
  (balance [_] (:debit-balance trx))
  (set-balance [_ balance]
    (-> trx
        (assoc :debit-balance balance)
        (split)
        :debit))
  (transaction-date [_] (:transaction-date trx))
  (description [_] (:description trx))
  (bilateral [_] trx))

(defn- pprintable
  [t]
  {:amount (format "%.2f" (amount t))
   :account (:name (account t))
   :description (description t)
   :transaction-date (unparse (:date formatters)
                              (to-date-time (transaction-date t)))
   :index (index t)
   :balance (when-let [b (balance t)]
              (format "%.2f" b))})

(defmethod pp/simple-dispatch CreditSide
  [t]
  (pp/pprint (pprintable t) *out*))

(defmethod pp/simple-dispatch DebitSide
  [t]
  (pp/pprint (pprintable t) *out*))

(defn- split
  "Accepts a storable transaction and returns reified UnilateralTransaction instances,
  one for debit and one for credit"
  [trx]
  {:pre [trx *accounts*]}

  {:debit (DebitSide. trx *accounts*)
   :credit (CreditSide. trx *accounts*)})

(defn- split-and-filter
  [account trxs]
  (->> trxs
       (mapcat (comp vals split))
       (filter #(= (:id account)
                   (account-id %)))))

(defn select-by-account
  [account start-date end-date]
  (with-accounts account
    (->> (select {:account-id (:id account)
                  :start-date start-date
                  :end-date end-date})
         (split-and-filter account)
         (sort-by index))))

(defn- apply-account-id
  [query]
  (-> query
      (update-in [:where]
                 conj
                 '(or [id :transaction/debit-account-id account-id]
                      [id :transaction/credit-account-id account-id]))))

(defn- precedent
  [{:keys [transaction-date id]} account]
  (let [query (-> base-query
                  (apply-account-id)
                  (update-in [:where]
                             conj
                             ['(< transaction-date d)])
                  (assoc :order-by [['transaction-date :desc]] ; TODO: put back index
                         :limit 2
                         :in '[[account-id d]]))]
    (->> (mny/select query
                     [(:id account)
                      (->storable-date transaction-date)])
         (map after-read)
         (remove #(= id (:id %)))
         (split-and-filter account)
         first)))

(defn- subsequents
  [{:keys [transaction-date id]} account]
  (let [query (-> base-query
                  (apply-account-id)
                  (update-in [:where]
                             conj
                             ['(<= d transaction-date)])
                  (assoc :order-by [['transaction-date :asc]] ; TODO: put back the index
                         :in '[[account-id d]]))]
    (->> (mny/select query
                     [(:id account)
                      (->storable-date transaction-date)])
         (remove #(= id (:id %)))
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

(def ^:private before-save
  ensure-model-type)

(defn- apply-index-and-balance
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

(defn- update-account
  [account balance transaction-date]
  (-> account
      (update-in [:first-trx-date] (fnil t/earliest (t/local-date 9999 12 31)) transaction-date)
      (update-in [:last-trx-date] t/latest transaction-date)
      (assoc :balance balance)))

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
  ([trx action account]
   (propagate* trx action account false))
  ([trx action account delete?]
   (let [prev (precedent trx account)
         following (subsequents trx account)
         ts (if delete?
              following
              (cons (get-in (split trx) [action])
                    following))]
     (->> ts
          (reduce apply-index-and-balance
                  {:last-index (if prev (index prev) 0)
                   :last-balance (if prev (balance prev) 0M)
                   :out []})
          :out
          (into [])))))

(defn- balance-for-account
  [trx account]
 (cond
   (= (:id account)
      (:debit-account-id trx)) (:debit-balance trx)
   (= (:id account)
      (:credit-account-id trx)) (:credit-balance trx)))

(defn- propagate
  [{:keys [debit-account credit-account transaction-date] :as trx}]
  {:pre [(= (:entity-id trx)
            (-> trx :debit-account :entity-id)
            (-> trx :credit-account :entity-id))]}

  (let [debit-side (propagate* trx :debit debit-account)
        credit-side (propagate* (first debit-side) :credit credit-account)]

    ; The calling fn expects the transaction to be in the first position
    (concat 
      (map before-save
           (concat credit-side
                   (rest debit-side)))
      ; TODO: the accounts should only be updated if the update chain makes it
      ; to the last transaction for the account
      [(update-account debit-account
                       (balance-for-account (last debit-side) debit-account)
                       transaction-date)
       (update-account credit-account
                       (balance-for-account (last credit-side) credit-account)
                                            transaction-date)])))

(defn- resolve-accounts
  [{:keys [debit-account-id credit-account-id] :as trx}]
  (-> trx
      (assoc :debit-account (get-in *accounts* [debit-account-id]))
      (assoc :credit-account (get-in *accounts* [credit-account-id]))))

(defn put
  [trx]
  {:pre [(s/valid? ::transaction trx)]}

  (with-accounts trx
    (-> (apply mny/submit
               (-> trx
                   resolve-accounts
                   propagate))
        first
        find)))

(defn delete
  [trx]
  {:pre [(s/valid? ::transaction trx)]}

  (with-accounts trx
    (let [{:keys [debit-account credit-account]} (resolve-accounts trx)
          debit-side (propagate* trx :debit debit-account true)
          credit-side (propagate* trx :credit credit-account true)
          propagated (map before-save
                          (concat credit-side
                                  (rest debit-side)))]
      (apply mny/submit (cons [::xt/delete (->id trx)] ; TODO: Can we avoid the explicit reference to the underlying database ns?
                              (remove #(= (:id trx)
                                          (:id %))
                                      propagated))))))
