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

(defn update-in-if
  [m k f & args]
  (if (= :absent (get-in m k :absent))
    m
    (apply update-in m k f args)))

#?(:clj (defn uuid []
          (java.util.UUID/randomUUID)))
