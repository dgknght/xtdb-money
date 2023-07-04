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

(defn ->set
  [v]
  (if (coll? v)
    (set v)
    #{v}))

(defn include-strategy
  [{:keys [only exclude]}]
  (cond
    only    (list 'xtdb-money.helpers/->set only)
    exclude `(complement ~(->set exclude))
    :else   '(constantly true)))

(defn ensure-opts
  [args]
  (if (map? (first args))
    args
    (cons {} args)))

(defmacro dbtest
  [test-name & body]
  (let [[opts & bod] (ensure-opts body)]
    `(deftest ~test-name
       (doseq [[name# config#] (filter (comp ~(include-strategy opts)
                                             first)
                                       (dbs))]
         (binding [*strategy* (keyword name#)]
           (testing (format "database strategy %s" name#)
             (mny/with-storage [config#]
               ~@bod)))))))
