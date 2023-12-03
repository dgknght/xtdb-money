(ns xtdb-money.models.entities-test
  (:require [clojure.test :refer [is use-fixtures]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.helpers :refer [reset-db
                                        dbtest]]
            [xtdb-money.test-context :refer [with-context
                                             find-user
                                             find-entity
                                             find-commodity]]
            [xtdb-money.models.entities :as ents]
            [xtdb-money.models.mongodb.ref]
            [xtdb-money.models.sql.ref]
            [xtdb-money.models.xtdb.ref]
            [xtdb-money.models.datomic.ref]))

(use-fixtures :each reset-db)

(def ^:private base-context
  {:users [{:email "john@doe.com"
            :given-name "John"
            :surname "Doe"}]})

(dbtest create-an-entity
  (with-context base-context
    (let [user (find-user "john@doe.com")
          result (ents/put {:user-id (:id user)
                            :name "Personal"})]
      (is (:id result)
          "The result contains an :id value")
      (is (comparable? {:name "Personal"}
                       result)
          "The result contains the correct attributes")
      (is (comparable? {:name "Personal"}
                       (ents/find result))
          "The retrieved map contains the correct attributes"))))

(def ^:private update-context
  (merge base-context
         {:entities [{:user-id "john@doe.com"
                      :name "Personal"}]
          :commodities [{:name "United States Dollar"
                         :symbol "USD"
                         :type :currency
                         :entity-id "Personal"}]}))

(dbtest update-an-entity
  (with-context update-context
    (let [entity (find-entity "Personal")
          commodity (find-commodity "USD")
          updated (ents/put (assoc entity :default-commodity-id (:id commodity)))]
      (is (= (:id commodity)
             (:default-commodity-id updated))
          "The result contains the updated attributes")
      (is (= (:id commodity)
             (:default-commodity-id (ents/find entity)))
          "A retrieved model has the updated attributes"))))

(dbtest fetch-all-entities
  (with-context update-context
    (is (seq-of-maps-like? [{:name "Personal"}]
                           (ents/select {:user-id (:id (find-user "john@doe.com"))})))))

(dbtest delete-an-entity
  (with-context update-context
    (let [entity (find-entity "Personal")
          res (ents/delete entity)]
      (is res "It returns a non-nil value")
      (is (nil? (ents/find (:id entity)))
          "The entity cannot be retrieved after delete"))))
