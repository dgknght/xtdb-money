(ns xtdb-money.api.entities-test
  (:require [clojure.test :refer [deftest is]]
            [dgknght.app-lib.test-assertions]
            [ring.mock.request :as req]
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
        (is (= [{:id 101 :name "Personal"}
                {:id 102 :name "Business"}]
               (:json-body res))
            "The entity data is returned")
        (is (= 1 (count cs))
            "The ents/select fn is called once")
        (is (= [{}] c)
            "The ents/select fn is called with the correct arguments")))))
