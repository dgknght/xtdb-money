(ns xtdb-money.api.users
  (:require [cljs.pprint :refer [pprint]]
            [dgknght.app-lib.api-3 :refer [path]]
            [xtdb-money.api :as api]))

(defn me
  [& {:as opts}]
  (api/get (path :me) opts) )
