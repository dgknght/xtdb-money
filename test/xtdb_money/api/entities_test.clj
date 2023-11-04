(ns xtdb-money.api.entities-test
  (:require [clojure.test :refer [deftest is testing]]
            [ring.mock.request :as req]
            [dgknght.app-lib.test-assertions]
            [dgknght.app-lib.web :refer [path]]
            [dgknght.app-lib.test :refer [parse-json-body]]
            [xtdb-money.api-context :refer [with-context]]
            [xtdb-money.helpers :refer [+auth
                                        +db-strategy]]
            [xtdb-money.models.entities :as ents]
            [xtdb-money.models.users :as usrs]
            [xtdb-money.handler :refer [app]]))

(def ^:private create-context
  {:user [{:id 101}]})

(deftest create-an-entity
  (with-context [create-context calls]
    (let [res (-> (req/request :post "/api/entities")
                  (req/json-body {:name "Personal"})
                  +db-strategy
                  (+auth {:id 101})
                  app
                  parse-json-body)
          [c :as cs] (get-in @calls [:entity :put])]
      (is (http-success? res))
      (is (= 1 (count cs))
          "The entities/put fn is called once")
      (is (= [{:name "Personal"}] c)
          "The entities/create fn is called with the correct arguments")
      (is (= {:id 123
              :name "Personal"}
             (:json-body res))
          "The created entity is returned"))))

(deftest get-a-list-of-entities
  (let [calls (atom [])]
    (with-redefs [ents/select (fn [& args]
                                (swap! calls conj args)
                                [{:id 101
                                  :name "Personal"}
                                 {:id 102
                                  :name "Business"}])
                  usrs/find (fn [id]
                              (when (= 101 id)
                                {:id 101}))]
      (let [res (-> (req/request :get "/api/entities")
                    (+db-strategy :test)
                    (+auth {:id 101})
                    app
                    parse-json-body)
            [c :as cs] @calls]
        (is (http-success? res))
        (is (comparable? {"Content-Type" "application/json; charset=utf-8"}
                         (:headers res))
            "The response has the correct content type")
        (is (= [{:id 101 :name "Personal"}
                {:id 102 :name "Business"}]
               (:json-body res))
            "The entity data is returned")
        (is (= 1 (count cs))
            "The ents/select fn is called once")
        (is (= [{}] c)
            "The ents/select fn is called with the correct arguments")))))

(deftest an-unauthenticated-user-cannot-get-a-list-of-entities
  (let [calls (atom [])]
    (with-redefs [ents/select (fn [& args]
                                (swap! calls conj args)
                                [{:id 101
                                  :name "Personal"}
                                 {:id 102
                                  :name "Business"}])
                  usrs/find (constantly nil)]
      (testing "no authorization header")
      (let [res (-> (req/request :get "/api/entities")
                    app
                    parse-json-body)]
        (is (http-unauthorized? res))
        (is (empty? @calls))))))

(deftest update-an-entity
  (testing "update an existing entity"
    (let [calls (atom [])]
      (with-redefs [ents/put (fn [& args]
                               (swap! calls conj args)
                               (first args))
                    ents/select (constantly [{:id 101
                                              :name "The old name"}])
                    usrs/find (fn [id]
                                (when (= 101 id)
                                  {:id 101}))]
        (let [res (-> (req/request :patch (path :api :entities 101))
                      (req/json-body {:name "The new name"})
                      (+auth {:id 101})
                      app
                      parse-json-body)
              [c :as cs] @calls]
          (is (http-success? res))
          (is (= 1 (count cs))
              "The entities update fn is called once")
          (is (= [{:id 101
                   :name "The new name"}]
                 c)
              "The entities update fn is called with the updated entity map")
          (is (= {:id 101
                  :name "The new name"}
                 (:json-body res))
              "The result of the update fn is returned")))))
  (testing "attempt to update an non-existing entity"
    (let [calls (atom [])]
      (with-redefs [ents/put (fn [& args]
                               (swap! calls conj args)
                               (first args))
                    ents/select (constantly [])
                    usrs/find (fn [id]
                                (when (= 101 id)
                                  {:id 101}))]
        (is (http-not-found? (-> (req/request :patch (path :api :entities 101))
                                 (req/json-body {:name "The new name"})
                                 (+auth {:id 101})
                                 app
                                 parse-json-body)))
        (is (zero? (count @calls))
            "The entities update fn is not called")))))

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
