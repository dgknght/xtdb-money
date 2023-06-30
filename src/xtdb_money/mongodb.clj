(ns xtdb-money.mongodb
  (:require [clojure.set :refer [rename-keys]]
            [somnium.congomongo :as m]
            [xtdb-money.core :as mny]
            [dgknght.app-lib.inflection :refer [plural]]))

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

(defn- select*
  [_conn _criteria _options])

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
