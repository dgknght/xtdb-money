(ns xtdb-money.sql.queries
  (:refer-clojure :exclude [format])
  (:require [clojure.pprint :refer [pprint]]
            [honey.sql :refer [format]]
            [honey.sql.helpers :refer [select
                                       from]]
            [stowaway.sql :refer [apply-criteria]]
            [dgknght.app-lib.core :refer [update-in-if]]
            [dgknght.app-lib.inflection :refer [plural]]
            [xtdb-money.core :as mny]
            [xtdb-money.sql.types :refer [coerce-id]]))

(derive clojure.lang.PersistentArrayMap ::map)
(derive clojure.lang.PersistentVector ::vector)

(def infer-table-name
  (comp plural
        mny/model-type))

(defn- apply-options
  [s {:keys [limit order-by]}]
  (cond-> s
    limit (assoc :limit limit)
    order-by (assoc :order-by order-by)))

(def ^:private query-options
  {:relationships {#{:user :identity} {:primary-table :users
                                       :foreign-table :identities
                                       :foreign-id :user_id}}})

(defn criteria->query
  [criteria & [options]]
  {:pre [(mny/model-type criteria)]}

  (-> (select :*)
      (from (infer-table-name criteria))
      (apply-criteria (update-in-if criteria [:id] coerce-id)
                      (merge query-options
                             {:target (mny/model-type criteria)}))
      (apply-options options)
      format))
