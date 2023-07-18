(ns xtdb-money.models.xtdb.accounts
  (:require [dgknght.app-lib.core :refer [update-in-if]]
            [xtdb-money.util :refer [<-storable-date]]
            [xtdb-money.xtdb :as x]))

(defmethod x/after-read :account
  [account]
  (-> account
      (update-in-if [:first-trx-date] <-storable-date)
      (update-in-if [:last-trx-date] <-storable-date)))
