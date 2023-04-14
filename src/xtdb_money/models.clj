(ns xtdb-money.models
  (:require [clojure.spec.alpha :as s]))

(s/def ::entity-id uuid?)
