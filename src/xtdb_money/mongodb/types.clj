(ns xtdb-money.mongodb.types
  (:require [cheshire.generate :refer [add-encoder]]
            [clj-time.coerce :refer [to-local-date
                                     to-date]]
            [somnium.congomongo.coerce :refer [ConvertibleFromMongo
                                               ConvertibleToMongo]])
  (:import org.bson.types.ObjectId
           com.fasterxml.jackson.core.JsonGenerator
           org.joda.time.LocalDate
           java.util.Date
           org.bson.types.Decimal128
           org.bson.types.ObjectId))

(add-encoder ObjectId
             (fn [^ObjectId id ^JsonGenerator g]
               (.writeString g (str id))))

(extend-protocol ConvertibleToMongo
  LocalDate
  (clojure->mongo [^LocalDate d] (to-date d)))

(extend-protocol ConvertibleFromMongo
  Date
  (mongo->clojure [^Date d _kwd] (to-local-date d))

  Decimal128
  (mongo->clojure [^Decimal128 d _kwd] (.bigDecimalValue d)))

(defmulti coerce-id type)
(defmethod coerce-id :default [id] id)
(defmethod coerce-id String
  [id]
  (ObjectId. id))

(defn safe-coerce-id
  [id]
  (when id (coerce-id id)))
