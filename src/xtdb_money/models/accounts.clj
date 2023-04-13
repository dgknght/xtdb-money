(ns xtdb-money.models.accounts
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.core :as mny]))

(s/def ::name string?)
(s/def ::type #{:asset :liability :equity :income :expense})
(s/def ::balance decimal?)
(s/def ::account (s/keys :req-un [::name
                                  ::type]
                         :opt-un [::balance]))

(defn- ->model
  [model]
  (as-> model m
      (zipmap [:id :name :type :balance] m)
      (vary-meta m assoc :model-type :account)))

(defn select []
  (map ->model
       (mny/select
         '{:find [id name type balance]
           :where [[id :type :account]
                   [id :account/name name]
                   [id :account/type type]
                   [id :account/balance balance]]})))

(defn find
  [id]
  (->> (mny/select
                 '{:find [id name type balance]
                   :where [[id :type :account]
                           [id :account/name name]
                           [id :account/type type]
                           [id :account/balance balance]]
                   :in [id]}
                 id)
       (map ->model)
       first))

(defn find-by-name
  [account-name]
  (->> (mny/select
                 '{:find [id name type balance]
                   :where [[id :type :account]
                           [id :account/name name]
                           [id :account/type type]
                           [id :account/balance balance]]
                   :in [name]}
                 account-name)
       (map ->model)
       first))

(defn put
  [account]
  {:pre [(s/valid? ::account account)]}
  (-> account
      (update-in [:balance] (fnil identity 0M))
      (vary-meta assoc :model-type :account)
      (mny/put)))

(defn- left-side?
  [{:keys [type]}]
  (#{:asset :expense} type))

(defn debit
  [account amount]
  (let [f (if (left-side? account) + -)]
    (update-in account [:balance] f amount)))

(defn credit
  [account amount]
  (let [f (if (left-side? account) - +)]
    (update-in account [:balance] f amount)))
