(ns xtdb-money.mongodb
  (:require [clojure.set :refer [rename-keys]]
            [somnium.congomongo :as m]
            [xtdb-money.core :as mny]
            [dgknght.app-lib.inflection :refer [plural]]))

(defmulti after-read mny/model-type)
(defmethod after-read :default [m] m)

(def ^:private infer-collection-name
  (comp plural
        mny/model-type))

(defn- put*
  [conn models]
  (m/with-mongo conn
    (map #(rename-keys % {:_id :id})
         (m/insert! (infer-collection-name (first models))
                    models
                    {:many? true}))))

(defn- apply-criteria
  [query criteria]
  (if (seq criteria)
    (assoc query :where (rename-keys criteria {:id :_id}))
    query))

(defn- apply-options
  [query {:keys [limit order-by]}]
  (cond-> query
    limit (assoc :limit limit)
    order-by (assoc :sort order-by)))

(defn- select*
  [conn criteria options]
  (m/with-mongo conn
    (map (comp after-read
               #(mny/model-type % criteria))
         (m/fetch (infer-collection-name criteria)
                  (-> {}
                      (apply-criteria criteria)
                      (apply-options options))))))

(defn- delete*
  [_conn _models])

(defn- reset*
  [conn]
  (m/with-mongo conn
    (doseq [c [:entities]]
      (m/destroy! c {}))))

(defn connect
  [{:keys [database] :as config}]
  (m/make-connection database (dissoc config :database)))

(defmethod mny/reify-storage :mongodb
  [config]
  (let [conn (connect config)]
    (reify mny/Storage
      (put [_ models]       (put* conn models))
      (select [_ crit opts] (select* conn crit opts))
      (delete [_ models]    (delete* conn models))
      (reset [_]            (reset* conn)))))
