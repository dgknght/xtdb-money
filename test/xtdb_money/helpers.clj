(ns xtdb-money.helpers
  (:require [clojure.test :refer [deftest testing]]
            [config.core :refer [env]]
            [xtdb-money.core :as mny]))

(defn dbs []
  (get-in env [:db :strategies]))

(defn reset-db [f]
  (let [dbs (->> (get-in env [:db :strategies])
                 vals
                 (mapv mny/reify-storage))]
    (doseq [db dbs]
      (mny/reset db))
    (f)))

(def ^:dynamic *strategy* nil)

(defmacro dbtest
  [test-name & body]
  `(deftest ~test-name
     (doseq [[name# config#] (dbs)]
       (binding [*strategy* (keyword name#)]
         (testing (format "database strategy %s" name#)
           (mny/with-storage [config#]
             ~@body))))))
