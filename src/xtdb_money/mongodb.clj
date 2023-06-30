(ns xtdb-money.mongodb
  (:require [clojure.set :refer [rename-keys]]
            [somnium.congomongo :as m]
            [xtdb-money.core :as mny]))

(defn- put*
  [conn models]
  (m/with-mongo conn
    (map #(rename-keys % {:_id :id})
         (m/insert! :entities models {:many? true}))))

(defn- select*
  [_conn _criteria _options])

(defn- delete*
  [_conn _models])

(defn- reset*
  [_conn])

(defmethod mny/reify-storage :mongodb
  [{:keys [database] :as config}]
  (let [conn (m/make-connection database (dissoc config :database))]
    (reify mny/Storage
      (put [_ models]       (put* conn models))
      (select [_ crit opts] (select* conn crit opts))
      (delete [_ models]    (delete* conn models))
      (reset [_]            (reset* conn)))))
