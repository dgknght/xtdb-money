(ns xtdb-money.models.datomic.accounts
  (:require [dgknght.app-lib.core :refer [update-in-if]]
            [xtdb-money.util :refer [->storable-date
                                     <-storable-date]]
            [xtdb-money.datomic :as d]))

(defmethod d/before-save :account
  [account]
  (-> account
      (update-in-if [:first-trx-date] ->storable-date)
      (update-in-if [:last-trx-date] ->storable-date)))

(defmethod d/after-read :account
  [account]
  (-> account
      (update-in-if [:first-trx-date] <-storable-date)
      (update-in-if [:last-trx-date] <-storable-date)
      (update-in [:entity-id] :id)))
