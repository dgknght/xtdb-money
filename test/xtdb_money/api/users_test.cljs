(ns xtdb-money.api.users-test
  (:require [cljs.test :refer [deftest is async]]
            [xtdb-money.api.users :as usrs]))

(deftest fetch-my-profile
  (async
    done
    (usrs/me :on-success (fn [user]
                           (is (= {:email "john@doe.com"}
                                  user))
                           (done))
             :on-error (fn [e]
                         (is false e)
                         (done)))))
