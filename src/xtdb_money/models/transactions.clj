(ns xtdb-money.models.transactions
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [clojure.pprint :refer [pprint] :as pp]
            [clj-time.core :as t]
            [clj-time.format :refer [formatters unparse]]
            [clj-time.coerce :refer [to-date-time]]
            [xtdb-money.util :refer [<-storable-date
                                     local-date?
                                     non-nil?]]
            [xtdb-money.core :as mny]
            [xtdb-money.accounts :as a]
            [xtdb-money.models :as mdls]
            [xtdb-money.models.accounts :as acts]))

(s/def ::correlation-id (s/nilable uuid?))
(s/def ::transaction-date local-date?)
(s/def ::description string?)
(s/def ::debit-account-id non-nil?)
(s/def ::credit-account-id non-nil?)
(s/def ::quantity (s/and decimal?
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
                                      ::quantity]
                             :opt-un [::mdls/id
                                      ::debit-index
                                      ::debit-balance
                                      ::credit-index
                                      ::credit-balance
                                      ::correlation-id]))

(def ^:private attributes
  [:id
   :entity-id
   :transaction-date
   :description
   :credit-account-id
   :debit-account-id,
   :quantity
   :debit-index
   :debit-balance
   :credit-index
   :credit-balance
   :correlation-id])

(defn- criterion?
  [pred]
  (some-fn pred
           (every-pred vector?
                       #(mny/oper? (first %))
                       #((criterion? pred) (second %)))))

(s/def ::transaction-date (criterion? local-date?))

(defn criteria-dispatch
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
                   ::transaction-date]))
(defmethod criteria :entity-id
  [_]
  (s/keys :req-un [::entity-id
                   ::transaction-date]))

(s/def ::criteria (s/multi-spec criteria :account-id))

(def ^{:private true :dynamic true} *accounts* nil)

(defmacro with-accounts
  "Provides contextual access to all of the accounts in the entity"
  [model & body]
  `(binding [*accounts* (->> (acts/select (select-keys ~model [:entity-id]))
                             (map (juxt :id identity))
                             (into {}))]
     ~@body))

(defn- after-read
  [trx]
  (-> trx
      (update-in [:transaction-date] <-storable-date)
      (mny/set-meta :transaction)))

(def ^:private default-opts
  {:order-by [[:transaction-date :asc]]})

(defn select
  ([criteria]
   (select criteria {}))
  ([criteria options]
   {:pre [(s/valid? ::criteria criteria)
          (s/valid? ::mny/options options)]}
   (map after-read (mny/select (mny/storage)
                               (mny/model-type criteria :transaction)
                               (merge default-opts options)))))

(defn- mark-deleted
  [trx]
  (vary-meta trx assoc :deleted? true))

(defprotocol UnilateralTransaction
  (account [this] "Returns the account for the transaction")
  (account-id [this] "Retrieves the ID for the account to which the transaction belongs")
  (other-account [this] "Returns the account on the other side of the transaction")
  (quantity [this] "The polarized quantity of the transaction")
  (index [this] "Ordinal position of this transaction within the account")
  (set-index [this index] "Set the ordinal position")
  (balance [this] "Balance of the account as of this transaction")
  (set-balance [this balance] "Set the balance")
  (transaction-date [this] "The date of the transaction")
  (description [this] "The description of the transaction")
  (action [this] "The action of this side of the transaction")
  (bilateral [this] "Returns the underlaying bilateral transaction"))

(defn- transaction?
  [m]
  (= :transaction (mny/model-type m)))

(defn- account?
  [m]
  (= :account (mny/model-type m)))

(defmulti ^:private deleted?
  #(cond
     (satisfies? UnilateralTransaction %) :unilateral
     (map? %) :map))

(defmethod deleted? :unilateral
  [t]
  (deleted? (bilateral t)))

(defmethod deleted? :map
  [m]
  (-> m meta :deleted?))

(def ^:private unilateral? (partial satisfies? UnilateralTransaction))

(declare split)

(defrecord CreditSide [trx accounts]
  UnilateralTransaction
  (account [_] (-> trx :credit-account-id accounts))
  (account-id [_] (:credit-account-id trx))
  (other-account [_] (-> trx :debit-account-id accounts))
  (quantity [this]
    (a/polarize {:quantity (:quantity trx)
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
  (action [_] :credit)
  (bilateral [_] trx))

(defrecord DebitSide [trx accounts]
  UnilateralTransaction
  (account [_] (-> trx :debit-account-id accounts))
  (account-id [_] (:debit-account-id trx))
  (other-account [_] (-> trx :credit-account-id accounts))
  (quantity [this]
    (a/polarize {:quantity (:quantity trx)
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
  (action [_] :debit)
  (bilateral [_] trx))

(defmulti ^:private other-side class)

(defmethod other-side DebitSide
  [trx]
  (:credit (split (bilateral trx))))

(defmethod other-side CreditSide
  [trx]
  (:debit (split (bilateral trx))))

(defn- pprintable
  [t]
  {:quantity (format "%.2f" (quantity t))
   :action (action t)
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
  {:pre [(transaction? trx) *accounts*]}

  {:debit (DebitSide. trx *accounts*)
   :credit (CreditSide. trx *accounts*)})

(defn- split-and-filter
  [account trxs]
  (->> trxs
       (mapcat (comp vals split))
       (filter #(= (:id account)
                   (account-id %)))))

(defn between
  "Applies a :transaction-date attribute to a criteria map where
  :start-date is inclusive and :end-date is exclusive"
  [criteria start-date end-date]
  (assoc criteria :transaction-date [:and
                                     [:>= start-date]
                                     [:< end-date]]))

(defn select-by-account
  [account start-date end-date]
  (with-accounts account
    (->> (select (between {:account-id (:id account)}
                          start-date
                          end-date)
                 {})
         (split-and-filter account)
         (sort-by index))))

(defn- preceding
  [transaction-date account-id limit]
  (select {:transaction-date [:< transaction-date]
           :account-id account-id}
          {:limit limit
           :order-by [[:transaction-date :desc]]}))

(defn precedent
  ([unilateral] (precedent (bilateral unilateral)
                           (account unilateral)))
  ([{:keys [transaction-date id]} account]
   (->> (preceding transaction-date (:id account) 2)
        (remove #(= id (:id %)))
        (split-and-filter account)
        first)))

(defn- subsequents*
  [transaction-date account-id]

  (select {:transaction-date [:>= transaction-date]
           :account-id account-id}
          {:order-by [:transaction-date]}))

(defn subsequents
  ([unilateral] (subsequents (bilateral unilateral) (account unilateral)))
  ([{:keys [transaction-date id]} account]
   (->> (subsequents* transaction-date (:id account))
        (remove #(= id (:id %)))
        (split-and-filter account))))

(defn find
  [id]
  (first (select {:id id} {:limit 1})))

(defn- ensure-model-type
  [m]
  (if (mny/model-type m)
    m
    (mny/model-type m :transaction)))

(defmulti ^:private propagate-rec
  (fn [_state m]
    (cond
      (unilateral? m) :transaction
      (account? m) :account)))

(defmethod propagate-rec :transaction
  [{:keys [last-index last-balance] :as result} trx]
  {:pre [result trx]}

  (if (deleted? trx)
    (update-in result [:out] conj trx)
    (let [idx (+ 1 last-index)
          bln (+ last-balance
                 (quantity trx))
          updated (-> trx
                      (set-index idx)
                      (set-balance bln))
          term? (not (mny/changed? (bilateral updated)))]
      (cond-> (assoc result
                     :last-index idx
                     :last-balance bln
                     :last-transaction-date (transaction-date trx))
        term? reduced
        (not term?) (update-in [:out] conj updated)))))

(defmethod propagate-rec :account
  [{:keys [last-transaction-date
           last-balance
           out]
    :as result}
   account]
  (let [updated (if (= 0 (count (remove deleted? out)))
                  (assoc account
                         :balance 0M
                         :first-trx-date nil
                         :last-trx-date nil)
                  (-> account
                      (update-in [:first-trx-date]
                                 (fnil t/earliest (t/local-date 9999 12 31))
                                 last-transaction-date)
                      (update-in [:last-trx-date]
                                 t/latest
                                 last-transaction-date)
                      (assoc :balance last-balance)))]
    (update-in result [:out] conj updated)))

(defn- apply-first-trx-date-change
  [trx prev updates]
  (if (and (nil? prev)
           (empty? (filter account? updates)))
    (conj updates (assoc (account trx)
                         :first-trx-date (when-not (deleted? trx)
                                           (transaction-date trx))))
    updates))

; To propagate, get the transaction immediately preceding the one being put
; this is the starting point for the index and balance updates.
; continue updating the chain until a transaction is unmodified
; Note that if this is an update, we'll also need to address anything changed
; by removing it from its old location
;
; This need to be done for both the debit and the credit account.
; Care must be taken to ensure that any transactions that are in
; both the debit and credit chains are only updated once
(defn- propagate-side
  [trx]
  (let [prev (precedent trx)
        following (subsequents trx)]
    (->> (concat (cons trx following)
                 [(account trx)])
         (reduce propagate-rec
                 (merge {:out []}
                        (if prev
                          {:last-index (index prev)
                           :last-balance (balance prev)}
                          {:last-index 0
                           :last-balance 0M})))
         :out
         (apply-first-trx-date-change trx prev)
         (into []))))

(defn- bilateralize
  [trx]
  (if (unilateral? trx)
    (bilateral trx)
    trx))

; TODO: Maybe this should be done in a more central location for all model types?
(defn- prune
  [trx]
  (if (= :transaction
         (mny/model-type trx))
    (select-keys trx attributes)
    trx))

(defn- propagate
  [trx]
  {:pre [(= (:entity-id trx)
            (-> trx :debit-account :entity-id)
            (-> trx :credit-account :entity-id))]}
  (let [{:keys [debit]} (split trx)
        debit-side (propagate-side debit)
        credit-side (propagate-side (other-side (first debit-side)))]

    ; The calling fn expects the transaction to be in the first position
    (->> (rest debit-side)
         (concat credit-side)
         (map (comp ensure-model-type
                    prune
                    bilateralize))
         (remove deleted?)
         (into []))))

(defn- resolve-accounts
  [{:keys [debit-account-id credit-account-id] :as trx}]
  (-> trx
      (assoc :debit-account (get-in *accounts* [debit-account-id]))
      (assoc :credit-account (get-in *accounts* [credit-account-id]))))

(defn put
  [trx]
  {:pre [(s/valid? ::transaction trx)]}
  (with-accounts trx
    (let [result (first (mny/put (mny/storage)
                                 (-> trx
                                     (mny/model-type :transaction)
                                     resolve-accounts
                                     propagate)))]
      (if (map? result)
        (mny/model-type result :transaction)
        (find result)))))

(defn delete
  [trx]
  {:pre [trx (s/valid? ::transaction trx)]}

  (with-accounts trx
    (mny/put (mny/storage)
             (cons [::mny/delete trx]
                   (propagate (mark-deleted (resolve-accounts trx)))))))
