(ns xtdb-money.models
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.util :refer [non-nil?]]))

(s/def ::id non-nil?)
