(ns xtdb-money.models.datomic.accounts
  #_(:require [xtdb-money.models.accounts :as acts]
            [xtdb-money.datomic :as d]
            [xtdb-money.util :refer [unqualify-keys]]))

#_(defn- after-read
  [account]
  (-> account
      (update-in [:entity-id] :id)
      (update-in [:type] keyword)))

#_(defmethod acts/query :datomic
  [criteria]
  {:pre [(:entity-id criteria)]}
  (map (comp after-read
             unqualify-keys)
       (d/index-pull {:index :avet
                      :selector '[*]
                      :start [:account/entity-id (:entity-id criteria)]})))

#_(defn- remove-nils
  [account]
  (reduce (fn [a k]
            (if (a k)
              a
              (dissoc a k)))
          account
          (keys account)))

#_(defn- before-save
  [account]
  (-> account
      (update-in [:type] name)
      remove-nils))

#_(defmethod acts/submit :datomic
  [& models]
  (->> models
       (map before-save)
       d/transact))
