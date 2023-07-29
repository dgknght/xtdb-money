(ns xtdb-money.helpers
  (:require [clojure.test :refer [deftest testing]]
            [config.core :refer [env]]
            [xtdb-money.core :as mny]))

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

(def isolate (when-let [isolate (env :isolate)]
               #{isolate}))
(def ignore-strategy (if isolate
                       (complement isolate)
                       (->set (env :ignore-strategy))))
(def honor-strategy (complement ignore-strategy))

(defn dbs []
  (get-in env [:db :strategies]))

(defn reset-db [f]
  (let [dbs (->> (get-in env [:db :strategies])
                 (remove (comp ignore-strategy first))
                 vals
                 (mapv mny/reify-storage))]
    (doseq [db dbs]
      (mny/reset db))
    (f)))

(defn ensure-opts
  [args]
  (if (map? (first args))
    args
    (cons {} args)))

(defmacro dbtest
  [test-name & body]
  (let [[opts & bod] (ensure-opts body)]
    `(deftest ~test-name
       (doseq [[name# config#] (filter (comp (every-pred ~(include-strategy opts)
                                                         honor-strategy)
                                             first)
                                       (dbs))]
         (binding [*strategy* (keyword name#)]
           (testing (format "database strategy %s" name#)
             (mny/with-storage [config#]
               ~@bod)))))))
