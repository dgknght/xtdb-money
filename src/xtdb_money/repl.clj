(ns xtdb-money.repl
  (:require [xtdb-money.core :as mny]
            [xtdb-money.models.entities :as ents]
            [xtdb-money.models.accounts :as acts]))

(def entity (atom nil))


(def start mny/start)
(def stop mny/stop)

(defn entities []
  (doseq [[i e] (zipmap (iterate inc 1)
                        (ents/select))]
    (println (format "%s. %s" i (:name e)))))

(defn add-entity
  [entity]
  (ents/put entity))

(defmulti set-entity type)
(defmethod set-entity java.lang.Long
  [index]
  (reset! entity (->> (ents/select)
                     (drop (- index 1))
                     first)))
(defmethod set-entity java.lang.String
  [e-name]
  (reset! entity (->> (ents/select)
                     (filter #(= e-name (:name %)))
                     first)))

(defn accounts
  []
  {:pre [@entity]}

  (doseq [[i e] (zipmap (iterate inc 1)
                        (acts/select {:entity-id (:id @entity)}))]
    (println (format "%s. %s" i (:name e)))))

(defn add-account
  [account]
  {:pre [@entity]}
  (acts/put (assoc account :entity-id (:id @entity))))
