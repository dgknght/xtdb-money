(ns xtdb-money.models.accounts
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.core :as mny]
            [xtdb-money.accounts :refer [polarize]]
            [xtdb-money.models :as models]))

(s/def ::name string?)
(s/def ::type #{:asset :liability :equity :income :expense})
(s/def ::balance decimal?)
(s/def ::account (s/keys :req-un [::models/entity-id
                                  ::name
                                  ::type]
                         :opt-un [::balance]))

(defn- after-read
  [account]
  (with-meta account {:model-type :account}))

(defn select
  [{:keys [id entity-id] :as criteria}]
  {:per [(or (uuid? (:id criteria))
             (uuid? (:entity-id criteria)))]}

  (let [fields '[id entity-id name type balance]
        query (cond-> {:find fields
                       :keys fields
                       :where '[[id :account/entity-id entity-id]
                                [id :account/name name]
                                [id :account/type type]
                                [id :account/balance balance]]}
                entity-id (assoc :in '[entity-id])
                id (assoc :in '[id]))]
    (map after-read
         (mny/select query (or id entity-id)))))

(defn find
  [id]
  (first (select {:id id})))

(defn find-by-name
  [account-name]
  (first (select {:name account-name})))

(defn- find-first
  [[id]]
  (find id))

(defn put
  [account]
  {:pre [(s/valid? ::account account)]}

  (-> account
      (update-in [:balance] (fnil identity 0M))
      (vary-meta assoc :model-type :account)
      (mny/put)
      find-first))

(defn debit
  [account amount]
  (update-in account [:balance] + (polarize amount :debit account)))

(defn credit
  [account amount]
  (update-in account [:balance] + (polarize amount :credit account)))
