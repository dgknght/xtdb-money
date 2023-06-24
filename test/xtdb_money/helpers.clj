(ns xtdb-money.helpers
  (:require [clojure.test :refer [deftest testing]]
            [config.core :refer [env]]
            [xtdb-money.core :as mny]))

(defn dbs []
  (get-in env [:db :strategies]))

(defn reset-db [f]
  (let [dbs (->> (get-in env [:db :strategies])
                 vals
                 (map mny/reify-storage))]
    (doseq [db dbs]
      (mny/reset db)
      (binding [mny/*storage* db]
        (f)))))

(defmacro dbtest
  [test-name & body]
  `(deftest ~test-name
     (doseq [[name# config#] (dbs)]
       (testing (format "database strategy %s" name#)
         (mny/with-storage [config#]
           ~@body)))))
