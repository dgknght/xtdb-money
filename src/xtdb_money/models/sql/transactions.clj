(ns xtdb-money.models.sql.transactions
  (:require [clojure.walk :refer [postwalk]]
            [clojure.pprint :refer [pprint]]
            [dgknght.app-lib.core :refer [uuid
                                          update-in-if]]
            [xtdb-money.util :refer [local-date?]]
            [xtdb-money.sql.types :refer [->storable
                                          <-storable]]
            [xtdb-money.sql :as sql]))

; TODO: Dedupe this with what is also in mongodb
(def ^:private attr
  [:id
   :entity-id
   :transaction-date
   :description
   :quantity
   :debit-account-id
   :debit-index
   :debit-balance
   :credit-account-id
   :credit-index
   :credit-balance
   :correlation-id])

(defmethod sql/attributes :transaction [_] attr)

(defmethod sql/before-save :transaction
  [transaction]
  (-> transaction
      (update-in [:transaction-date] ->storable)
      (select-keys attr)))

(defmethod sql/after-read :transaction
  [transaction]
  (-> transaction
      (update-in-if [:correlation-id] uuid)
      (update-in [:transaction-date] <-storable)))

(defn- extract-account-id
  [{:keys [account-id] :as c}]
  (if account-id
    (with-meta [:and
                (dissoc c :account-id)
                [:or
                 {:debit-account-id account-id}
                 {:credit-account-id account-id}]]
               (meta c))
    c))

(defn- prepare-criteria
  [c]
  (cond-> c
    (map? c) extract-account-id
    (local-date? c) ->storable))

(defmethod sql/prepare-criteria :transaction
  [criteria]
  (postwalk prepare-criteria criteria))
