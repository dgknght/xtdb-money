(ns xtdb-money.util
  (:require #?(:clj [clj-time.coerce :as tc]
               :cljs [cljs-time.coerce :as tc]))
  #?(:clj (:import org.joda.time.LocalDate)))

(def ->storable-date tc/to-long)

(def <-storable-date tc/to-local-date)

(defn ->id
  [id-or-model]
  (or (:id id-or-model) id-or-model))

(def local-date?
  #?(:clj (partial instance? LocalDate)
     :cljs (throw (js/Error "Not implemented"))))
