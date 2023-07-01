(ns xtdb-money.models.mongodb.accounts
  (:require [xtdb-money.mongodb :as mdb]))

(defmethod mdb/after-read :account
  [account]
  (update-in account [:type] keyword))
