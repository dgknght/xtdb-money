(ns xtdb-money.api.entities-test
  (:require [cljs.test :refer [deftest is async]]
            [cljs.pprint :refer [pprint]]
            [xtdb-money.api.entities :as ents]))

(deftest get-a-list-of-entities
  (async
    done
    (ents/select :on-error (fn [e]
                             (is (nil? e) "No exception is thrown")
                             (done))
                 :callback
                 (fn [res]

                   (pprint {::res res})

                   (is (= [{:id 101
                            :name "Personal"}
                           [:id 102
                            :name "Business"]]
                          res)
                       "The callback returns the list of entities")
                   (done)))))
