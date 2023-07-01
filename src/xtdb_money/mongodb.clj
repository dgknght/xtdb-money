(ns xtdb-money.mongodb
  (:require [clojure.tools.logging :as log]
            [clojure.set :refer [rename-keys]]
            [clj-time.coerce :refer [to-local-date
                                     to-date]]
            [somnium.congomongo :as m]
            [somnium.congomongo.coerce :refer [ConvertibleFromMongo
                                               ConvertibleToMongo]]
            [xtdb-money.core :as mny]
            [dgknght.app-lib.inflection :refer [plural]])
  (:import org.joda.time.LocalDate
           java.util.Date))

(extend-protocol ConvertibleToMongo
  LocalDate
  (clojure->mongo [^LocalDate d] (to-date d)))

(extend-protocol ConvertibleFromMongo
  Date
  (mongo->clojure [^Date d _kwd] (to-local-date d)))

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

(defmulti ^:private adjust-complex-criterion
  (fn [[_k v]]
    (when (vector? v)
      (if (#{:and :or} (first v))
        :conjunction
        :comparison))))

(defn- ->mongodb-op
  [op]
  (keyword (str "$" (name op))))

(defmethod adjust-complex-criterion :default [c] c)

(defmethod adjust-complex-criterion :comparison
  [c]
  ; e.g. [:transaction-date [:< #inst "2020-01-01"]]
  ; ->   [:transcation-date [:$< #inst "2020-01-01"]]
  (update-in c [1 0] ->mongodb-op))

(defmethod adjust-complex-criterion :conjunction
  [[f [op & criteria]]]
  (apply vector
         (->mongodb-op op)
         (map (fn [c]
                [f c])
              criteria)))

(defn- adjust-complex-criteria
  [criteria]
  (->> criteria
       (map adjust-complex-criterion)
       (into {})))

(defn- apply-criteria
  [query criteria]
  (if (seq criteria)
    (assoc query :where (-> criteria
                            (rename-keys {:id :_id})
                            adjust-complex-criteria))
    query))

(defmulti ^:private ->mongodb-sort
  (fn [x]
    (if (vector? x)
      :explicit
      :implicit)))

(defmethod ->mongodb-sort :implicit
  [f]
  [f 1])

(defmethod ->mongodb-sort :explicit
  [sort]
  (update-in sort [1] #(if (= :asc %) 1 -1)))

(defn- apply-options
  [query {:keys [limit order-by]}]
  (cond-> query
    limit (assoc :limit limit)
    order-by (assoc :sort (mapv ->mongodb-sort order-by))))

(defn- select*
  [conn criteria options]
  (m/with-mongo conn
    (let [query (-> {}
                    (apply-criteria criteria)
                    (apply-options options))]
      (log/debugf "fetch %s with options %s -> %s" criteria options query)
      (map (comp after-read
                 #(rename-keys % {:_id :id})
                 #(mny/model-type % criteria))
           (m/fetch (infer-collection-name criteria)
                    query)))))

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
