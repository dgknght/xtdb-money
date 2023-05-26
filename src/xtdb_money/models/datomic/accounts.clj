(ns xtdb-money.models.datomic.accounts
  (:require [xtdb-money.models.accounts :as acts]
            [xtdb-money.datomic :as d]
            [xtdb-money.models :as models]))

(def ^:private account-keys [:id :name])

(defn- after-read
  [account]
  (update-in account [:type] keyword))

(defmethod acts/query :datomic
  [criteria]

  (clojure.pprint/pprint
    {::query (d/query '[:find ?a ?name ?type ?entity-id
                        :where [?a :account/name ?name]
                        [?a :account/type ?type]
                        [?a :account/entity-id ?entity-id]])})

  (d/index-pull {:index :avet
                 :selector '[:account/entity-id]
                 :start [:account/entity-id (:entity-id criteria)]})
  #_(map (comp after-read
               #(zipmap account-keys %))
         (d/query '[:find ?account-id ?account-entity-id ?account-name ?account-type ?account-balance ?account-first-trx-date ?account-last-trx-date
                    :where [?a :account/id ?account-id]
                    [?a :account/entity-id ?account-entity-id]
                    [?a :account/name ?account-name]
                    [?a :account/type ?account-type]
                    [?a :account/balance ?account-balance]
                    [?a :account/first-trx-date ?account-first-trx-date]
                    [?a :account/last-trx-date ?account-last-trx-date]])))

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
