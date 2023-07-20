(ns xtdb-money.models.commodities
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.util :refer [->id
                                     non-nil?]]
            [xtdb-money.core :as mny :refer [dbfn]]))

(s/def ::entity-id non-nil?)
(s/def ::name string?)
(s/def ::symbol string?)
(s/def ::type #{:currency :stock :fund})
(s/def ::commodity (s/keys :req-un [::entity-id
                                    ::name
                                    ::symbol
                                    ::type]))

(defn- after-read
  [commodity]
  (mny/set-meta commodity :commodity))

(defn select
  ([criteria]         (select criteria {}))
  ([criteria options] (select (mny/storage) criteria options))
  ([db criteria options]
   {:pre [(satisfies? mny/Storage db)
          (s/valid? ::mny/options options)
          ((some-fn :id :entity-id) criteria)]}
   (map after-read
        (mny/select db
                    (mny/model-type criteria :commodity)
                    options))))

(dbfn find
  [db id]
  (first (select db
                 {:id (->id id)}
                 {:limit 1})))

(defn- before-save
  [commodity]
  (mny/model-type commodity :commodity))

(dbfn put
  [db commodity]
  {:pre [(s/valid? ::commodity commodity)]}

  (let [ids (mny/put db [(before-save commodity)])]
    (find db (first ids))))
