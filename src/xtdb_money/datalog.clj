(ns xtdb-money.datalog
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.core :as mny]))

(def ^:private concat*
  (fnil (comp vec concat) []))

(def ^:private conj*
  (fnil conj []))

(def ^{:private true :dynamic true} *opts*
  {:coerce identity
   :args-key [:args]
   :query-prefix []
   :remap {}})

(defn- coerce
  [x]
  ((:coerce *opts*) x))

(defn- args-key []
  (:args-key *opts*))

(defn- model-type []
  (:model-type *opts*))

(defn- query-key
  [& ks]
  (concat (:query-prefix *opts*) ks))

(defmulti apply-criterion
  (fn [_query [_k v]]
    (when (vector? v)
      (case (first v)
        (:< :<= :> :>=) :comparison
        :and            :intersection
        :or             :union))))

(defn- arg-ident
  ([k] (arg-ident k nil))
  ([k prefix]
  (symbol (str "?" (name k) prefix))))

(defn- field-ref
  [k]
  (or ((:remap *opts*) k)
      (keyword (name (model-type))
               (name k))))

(defmethod apply-criterion :default
  [query [k v]]
  (let [input (arg-ident k "-in")]
    (-> query
        (update-in (query-key :where)
                   conj*
                   ['?x
                    (field-ref k)
                    input])
        (update-in (query-key :in) conj* input)
        (update-in (args-key) conj* (coerce v)))))

(defmethod apply-criterion :comparison
  [query [k [oper v]]]
  (let [arg (arg-ident k)
        input (arg-ident k "-in")]
    (-> query
        (update-in (args-key) conj* (coerce v))
        (update-in (query-key :in) conj* input)
        (update-in (query-key :where)
                   concat*
                   [['?x
                     (keyword (name (model-type))
                              (name k))
                     arg]
                    [(list (symbol (name oper))
                           arg
                           input)]]))))

(defmethod apply-criterion :intersection
  [query [k [_and & vs]]]
  ; 1. establish a reference to the model attribute
  ; 2. apply each comparison to the reference
  ; 3. decide if we need to wrap with (and ...)
  (let [attr (name k)
        input-refs (map (comp symbol
                              #(str "?" attr "-in-" %)
                              #(+ 1 %))
                        (range (count vs)))
        attr-ref (arg-ident k)
        field (keyword (name (model-type)) attr)]
    (-> query
        (update-in (args-key) concat* (map (comp coerce last) vs))
        (update-in (query-key :in)      concat* input-refs)
        (update-in (query-key :where)
                   concat*
                   (cons
                     ['?x field attr-ref]
                     (->> vs
                        (interleave input-refs)
                        (partition 2)
                        (map (fn [[input-ref [oper]]]
                               [(list (-> oper name symbol)
                                       attr-ref
                                       input-ref)]))))))))

(s/def ::args-key (s/coll-of keyword? :kind vector?))
(s/def ::query-prefix (s/coll-of keyword :kind vector?))
(s/def ::options (s/keys :opt-un [::args-key
                                  ::query-prefix]))

(defn apply-criteria
  ([query criteria] (apply-criteria query criteria {}))
  ([query criteria opts]
   {:pre [(s/valid? ::options opts)
          (mny/model-type criteria)]}
   (binding [*opts* (merge *opts*
                           opts
                           {:model-type (mny/model-type criteria)})]
     (reduce apply-criterion
             query
             criteria))))

(defmulti ^:private apply-sort
  (fn [_query order-by]
    (cond
      (vector? order-by) :multi
      order-by           :single)))

(defmethod apply-sort :single
  [query order-by]
  (assoc query :order-by [[(arg-ident order-by) :asc]]))

(defmethod apply-sort :multi
  [query order-by]
  (assoc query :order-by (mapv (fn [x]
                                 (if (vector? x)
                                   (update-in x [0] arg-ident)
                                   [(symbol (str "?" (name x))) :asc]))
                               order-by)))

(defn apply-options
  [query {:keys [limit offset order-by]}]
  (cond-> query
    limit (assoc :limit limit)
    offset (assoc :offset offset)
    order-by (apply-sort order-by)))
