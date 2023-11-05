(ns xtdb-money.api.entities-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [ring.mock.request :as req]
            [dgknght.app-lib.test-assertions]
            [dgknght.app-lib.web :refer [path]]
            [dgknght.app-lib.test :refer [parse-json-body]]
            [xtdb-money.test-context :refer [with-context
                                             find-user
                                             find-entity]]
            [xtdb-money.helpers :refer [reset-db
                                        +auth]]
            [xtdb-money.models.entities :as ents]
            [xtdb-money.models.users :as usrs]
            [xtdb-money.handler :refer [app]]))

(use-fixtures :each reset-db)

(def ^:private create-context
  {:users [{:email "john@doe.com"
            :given-name "John"
            :surname "Doe"}]})

(deftest create-an-entity
  (with-context create-context
    (let [user (find-user "john@doe.com")
          res (-> (req/request :post "/api/entities")
                  (req/json-body {:name "Personal"})
                  (+auth user)
                  app
                  parse-json-body)]
      (is (comparable? {:user-id (str (:id user)) ; This is probably specific to mongodb
                        :name "Personal"}
                       (:json-body res))
          "The created entity is returned"))))

(def ^:private list-context
  (-> create-context
      (update-in [:users] conj {:email "jane@doe.com"
                                :given-name "Jane"
                                :surname "Doe"})
      (assoc :entities [{:user-id "john@doe.com"
                         :name "Personal"}
                        {:user-id "john@doe.com"
                         :name "Business"}
                        {:user-id "jane@doe.com"
                         :name "Jane's Money"}])))

(deftest get-a-list-of-entities
  (with-context list-context
    (let [user (find-user "john@doe.com")
          res (-> (req/request :get "/api/entities")
                  (+auth user)
                  app
                  parse-json-body)]
      (is (http-success? res))
      (is (comparable? {"Content-Type" "application/json; charset=utf-8"}
                       (:headers res))
          "The response has the correct content type")
      (is (seq-of-maps-like?
            (map #(hash-map :user-id (str (:id user))
                            :name %)
                 ["Business" "Personal"])
            (:json-body res))
          "The entity data is returned"))))

(deftest an-unauthenticated-user-cannot-get-a-list-of-entities
  (with-context list-context
    (testing "no authorization header")
    (let [res (-> (req/request :get "/api/entities")
                  app
                  parse-json-body)]
      (is (http-unauthorized? res)))))

(deftest an-authenticated-user-can-update-his-entity
  (with-context list-context
    (testing "update an existing entity"
      (let [user (find-user "john@doe.com")
            entity (find-entity "Personal")
            res (-> (req/request :patch (path :api :entities (:id entity)))
                    (req/json-body {:name "The new name"})
                    (+auth user)
                    app
                    parse-json-body)]
        (is (http-success? res))
        (is (comparable? {:name "The new name"}
                         (:json-body res))
            "The result of the update fn is returned")
        (is (comparable? {:name "The new name"}
                         (ents/find entity))
            "The updated entity can be retrieved")))
    (testing "attempt to update an non-existing entity"
      (is (http-not-found? (-> (req/request :patch (path :api :entities 101))
                               (req/json-body {:name "The new name"})
                               (+auth (find-user "john@doe.com"))
                               app
                               parse-json-body))))))

(deftest an-authenticated-user-cannot-update-anothers-entity
  (with-context list-context
    (testing "update an existing entity"
      (let [user (find-user "john@doe.com")
            entity (find-entity "Jane's Money")
            res (-> (req/request :patch (path :api :entities (:id entity)))
                    (req/json-body {:name "The new name"})
                    (+auth user)
                    app
                    parse-json-body)]
        (is (http-not-found? res))
        (is (= entity
               (ents/find entity))
            "The entity is not updated in the database")))))

(deftest delete-an-entity
  (testing "delete an existing entity"
    (let [calls (atom [])]
      (with-redefs [ents/delete (fn [& args]
                                  (swap! calls conj args)
                                  nil)
                    ents/select (constantly [{:id 101
                                              :name "My Money"}])
                    usrs/find (fn [id]
                                (when (= 101 id)
                                  {:id 101}))]
        (let [res (-> (req/request :delete (path :api :entities 101))
                      (+auth {:id 101})
                      app)
              [c :as cs] @calls]
          (is (http-no-content? res))
          (is (= 1 (count cs))
              "The delete function is called once")
          (is (= [{:id 101
                   :name "My Money"}]
                 c)
              "The delete funtion is called with the correct arguments")))))
  (testing "attempt to delete a non-existing entity"
    (let [calls (atom [])]
      (with-redefs [ents/delete (fn [& args]
                                  (swap! calls conj args)
                                  nil)
                    ents/select (constantly [])
                    usrs/find (fn [id]
                                (when (= 101 id)
                                  {:id 101}))]
        (is (http-not-found? (-> (req/request :delete (path :api :entities 101))
                                 (+auth {:id 101})
                                 app)))
        (is (zero? (count @calls))
            "The delete fn is not called")))))
