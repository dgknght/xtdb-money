(ns xtdb-money.models.datomic.accounts
  (:require [xtdb-money.models.accounts :as acts]
            [xtdb-money.datomic :as d]
            [xtdb-money.util :refer [unqualify-keys]]))

(defn- after-read
  [account]
  (-> account
      (update-in [:entity-id] :id)
      (update-in [:type] keyword)))

(defmethod acts/query :datomic
  [criteria]
  {:pre [(:entity-id criteria)]}
  (map (comp after-read
             unqualify-keys)
       (d/index-pull {:index :avet
                      :selector '[*]
                      :start [:account/entity-id (:entity-id criteria)]})))

(defn- remove-nils
  [account]
  (reduce (fn [a k]
            (if (a k)
              a
              (dissoc a k)))
          account
          (keys account)))

(defn- before-save
  [account]
  (-> account
      (update-in [:type] name)
      remove-nils))

(defmethod acts/submit :datomic
  [& models]
  (->> models
       (map before-save)
       d/transact))
