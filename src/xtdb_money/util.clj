(ns xtdb-money.util
  (:require [clj-time.coerce :as tc]))

(def ->storable-date tc/to-long)

(def <-storable-date tc/to-local-date)
