(ns xtdb-money.core
  (:require [clojure.walk :refer [postwalk]]
            [clojure.spec.alpha :as s]
            [xtdb.api :as xt])
  (:gen-class))


(defonce ^:private node (atom nil))

(defn start []
  (reset! node (xt/start-node {})))

(defn stop []
  (reset! node nil))

(defn- put
  [node & docs]
  {:pre [(seq docs)]}

  (let [n @node]
    (xt/submit-tx n (->> docs
                         (map #(vector ::xt/put %))
                         (into [])))
    (xt/sync n)))

(defn- two-count?
  [coll]
  (= 2 (count coll)))

(defn- f-keyword?
  [[x]]
  (keyword? x))

(def map-tuple?
  (every-pred vector?
              two-count?
              f-keyword?))

(defmulti ^:private ->xt*
  (fn [x _]
    (when (map-tuple? x)
      :tuple)))

(defmethod ->xt* :default
  [x _]
  x)

(defmethod ->xt* :tuple
  [x model-type-name]
  (if (= :id (first x))
    (assoc-in x [0] :xt/id)
    (update-in x [0] #(keyword model-type-name
                               (name %)))))

(defn- ->xt-keys
  [m model-type]
  (postwalk #(->xt* % (name model-type)) m))

(defn- make-id
  [id]
  (if id id (java.util.UUID/randomUUID)))

(defn- ->xt-map
  [m model-type]
  (-> m
      (update-in [:id] make-id)
      (->xt-keys model-type)
      (assoc :type model-type)))

(defn accounts []
  (map
    #(zipmap [:id :name :type :balance]
              %)
    (xt/q (xt/db @node)
          '{:find [id name type balance]
            :where [[id :type :account]
                    [id :account/name name]
                    [id :account/type type]
                    [id :account/balance balance]]})))

(defn find-account
  [id]
  (first (map
           #(zipmap [:id :name :type :balance]
                    %)
           (xt/q (xt/db @node)
                 '{:find [id name type balance]
                   :where [[id :type :account]
                           [id :account/name name]
                           [id :account/type type]
                           [id :account/balance balance]]
                   :in [id]}
                 id))))

(defn find-account-by-name
  [account-name]
  (first (map
           #(zipmap [:id :name :type :balance]
                    %)
           (xt/q (xt/db @node)
                 '{:find [id name type balance]
                   :where [[id :type :account]
                           [id :account/name name]
                           [id :account/type type]
                           [id :account/balance balance]]
                   :in [name]}
                 account-name))))

(s/def ::name string?)
(s/def ::type #{:asset :liability :equity :income :expense})
(s/def ::balance decimal?)
(s/def ::account (s/keys :req-un [::name
                                  ::type]
                         :opt-un [::balance]))

(defn put-account
  [account]
  {:pre [(s/valid? ::account account)]}
  (put node (-> account
                (update-in [:balance] (fnil identity 0M))
                (->xt-map :account))))

(defn- left-side?
  [{:keys [type]}]
  (#{:asset :expense} type))

(defn- debit-account
  [account amount]
  (let [f (if (left-side? account) + -)]
    (update-in account [:balance] f amount)))

(defn- credit-account
  [account amount]
  (let [f (if (left-side? account) - +)]
    (update-in account [:balance] f amount)))

(defn create-transaction
  [{:keys [debit-account-id credit-account-id amount] :as trx}]
  (let [d-account (find-account debit-account-id)
        c-account (find-account credit-account-id)]
    (put node
         (->xt-map trx :transaction)
         (-> d-account
             (debit-account amount)
             (->xt-map :account))
         (-> c-account
             (credit-account amount)
             (->xt-map :account)))))
