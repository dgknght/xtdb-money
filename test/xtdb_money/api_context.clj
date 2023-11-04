(ns xtdb-money.api-context
  (:require [clojure.pprint :refer [pprint]]
            [xtdb-money.core :as mny]))

(def ^:dynamic *context* nil)
(def ^:dynamic *calls* nil)

(defn- log-call
  [m action args]
  (swap! *calls*
         update-in
         [(mny/model-type m) action]
         (fnil conj [])
         args))

(defn- put*
  [& [[model :as models] :as args]]
  (log-call (first model) :put args)
  (swap! *context* (fn [ctx]
                     (reduce (fn [c m]
                               ; TODO: add update logic
                               (update-in c
                                          [(mny/model-type m)]
                                          (fnil conj [])
                                          m))
                             ctx
                             models))))

(defn- select*
  [& [criteria _opts :as args]]
  (log-call criteria :select args)
  (let [models (get-in @*context* [(mny/model-type criteria)])
        result (->> models
                    (filter (fn [m]
                              (= (select-keys m (keys criteria))
                                 criteria)))
                    (map #(mny/model-type % criteria)))]
    
    (binding [*print-meta* true]
      (clojure.pprint/pprint {::select criteria
                              ::result result}))

    result))

(defn- delete*
  [models]
  (pprint {::delete models}))

(defmethod mny/reify-storage :mock
  [& _]
  (reify mny/Storage
    (put    [_ models]           (put* models))
    (select [_ criteria options] (select* criteria options))
    (delete [_ models]           (delete* models))
    (close  [_])
    (reset  [_])))

(defmacro with-context
  [bindings & body]
  `(let [f# (fn* [~(second bindings)]
                 ~@body)]
     (binding [*context* (atom ~(first bindings))
               *calls* (atom {})]
       (f# *calls*))))
