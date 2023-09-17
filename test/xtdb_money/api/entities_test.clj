(ns xtdb-money.api.entities-test
  (:require [clojure.test :refer [deftest is testing]]
            [ring.mock.request :as req]
            [dgknght.app-lib.test-assertions]
            [dgknght.app-lib.web :refer [path]]
            [dgknght.app-lib.test :refer [parse-json-body]]
            [xtdb-money.models.entities :as ents]
            [xtdb-money.handler :refer [app]]))

(deftest create-an-entity
  (let [calls (atom [])]
    (with-redefs [ents/put (fn [& args]
                              (swap! calls conj args)
                              (assoc (first args) :id 123))]
      (let [res (-> (req/request :post "/api/entities")
                    (req/json-body {:name "Personal"})
                    app
                    parse-json-body)
            [c :as cs] @calls]
        (is (http-success? res))
        (is (= 1 (count cs))
            "The entities/put fn is called once")
        (is (= [{:name "Personal"}] c)
            "The entities/create fn is called with the correct arguments")
        (is (= {:id 123
                :name "Personal"}
               (:json-body res))
            "The created entity is returned")))))

(deftest get-a-list-of-entities
  (let [calls (atom [])]
    (with-redefs [ents/select (fn [& args]
                                (swap! calls conj args)
                                [{:id 101
                                  :name "Personal"}
                                 {:id 102
                                  :name "Business"}])]
      (let [res (-> (req/request :get "/api/entities")
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

(deftest update-an-entity
  (testing "update an existing entity"
    (let [calls (atom [])]
      (with-redefs [ents/put (fn [& args]
                               (swap! calls conj args)
                               (first args))
                    ents/select (constantly [{:id 101
                                              :name "The old name"}])]
        (let [res (-> (req/request :patch (path :api :entities 101))
                      (req/json-body {:name "The new name"})
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
                    ents/select (constantly [])]
        (is (http-not-found? (-> (req/request :patch (path :api :entities 101))
                                 (req/json-body {:name "The new name"})
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
                                              :name "My Money"}])]
        (let [res (-> (req/request :delete (path :api :entities 101))
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
                    ents/select (constantly [])]
        (is (http-not-found? (-> (req/request :delete (path :api :entities 101))
                                 app)))
        (is (zero? (count @calls))
            "The delete fn is not called")))))
