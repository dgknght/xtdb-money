(ns xtdb-money.datalog
  (:require [clojure.spec.alpha :as s]
            [clojure.pprint :refer [pprint]]))

(derive clojure.lang.PersistentArrayMap ::map)
(derive clojure.lang.PersistentVector ::vector)

(s/def ::args-key (s/coll-of keyword? :kind vector?))
(s/def ::query-prefix (s/coll-of keyword :kind vector?))
(s/def ::model-type keyword?)
(defmulti options-spec type)
(defmethod options-spec ::map [_]
  (s/keys :req-un [::model-type]
          :opt-un [::args-key
                   ::query-prefix]))
(defmethod options-spec ::vector [_]
  (s/cat :operator #{:and :or}
         :criteria (s/+ ::options)))
(s/def ::options (s/multi-spec options-spec type))

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

(defn- arg-ident
  ([k] (arg-ident k nil))
  ([k suffix]
  (symbol (str "?" (name k) suffix))))

(defn- field-ref
  [k & {explicit-type :model-type}]
  (or ((:remap *opts*) k)
      (keyword (name (or explicit-type
                         (model-type)))
               (name k))))

(defmulti dissect
  "Accepts a criterion and returns a map containing elements
  to be merged with :where, :args, and :in in the final
  datalog query."
  (fn [[_k v]]
    (cond
      (map? v) :compound
      (vector? v) (case (first v)
                    (:< :<= :> :>=) :comparison
                    :and            :intersection
                    :or             :union
                    :=              :equality))))

(defmethod dissect :default
  [[k v]]
  (let [arg-in (arg-ident k "-in")]
    {:where [['?x
              (field-ref k)
              arg-in]]
     :args [(coerce v)]
     :in [arg-in]}))

(defmethod dissect :equality
  [[k [_op v]]]
  ; note the destructing is different from above, but the rest is the same
  (let [arg-in (arg-ident k "-in")]
    {:where [['?x
            (field-ref k)
            arg-in]]
   :args [(coerce v)]
   :in [arg-in]}))

(defmethod dissect :comparison
  [[k [oper v]]]
  (let [arg (arg-ident k)
        arg-in (arg-ident k "-in")]
    {:where [['?x
              (field-ref k)
              arg]
             [(list (symbol (name oper))
                    arg
                    arg-in)]]
     :in [arg-in]
     :args [(coerce v)]}))

; {:count [:and [:< 5] [:>= 1]]} -> {:find [?x]
;                                    :in [?count-1 ?count-2]
;                                    :args [5 1]
;                                    :where [[?x :model/count ?count]
;                                            [(< ?count ?count-1)]]
;                                            [(>= ?count ?count-2)]}
(defmethod dissect :intersection
  [[k [_and & vs]]]
  (let [attr-ref (arg-ident k)
        input-refs (mapv (comp symbol
                               #(str "?" (name k) "-in-" %)
                               #(+ 1 %))
                         (range (count vs)))]
    {:where (cons
              ['?x (field-ref k) attr-ref]
              (->> vs
                   (interleave input-refs)
                   (partition 2)
                   (map (fn [[input-ref [oper]]]
                          [(list (-> oper name symbol)
                                 attr-ref
                                 input-ref)]))))
     :args (map (comp coerce last) vs)
     :in input-refs}))

(defmethod dissect :union
  [[k criterion]]
  )

(defn- apply-criterion
  [query criterion]
  (let [parts (dissect criterion)]
    (-> query
        (update-in (args-key)         concat* (:args parts))
        (update-in (query-key :in)    concat* (:in parts))
        (update-in (query-key :where) concat* (:where parts)))))

(defmacro ^:private with-options
  [opts & body]
  `(binding [*opts* (merge *opts* ~opts)]
     ~@body))

(defmulti apply-criteria
  (fn [_q c _o]
    (type c)))

(defmethod apply-criteria ::map
  [query criteria opts]
  {:pre [(s/valid? ::options opts)]}

  (with-options opts criteria
    (reduce apply-criterion
            query
            criteria)))

(defmethod apply-criteria ::vector
  [query [oper & criterias] opts]
  {:pre [(s/valid? ::options opts)]}

  (let [parts (mapcat (fn [criteria]
                        (with-options opts criteria
                          (mapv dissect criteria)))
                    criterias)]
    (-> query
        (update-in (query-key :in) concat* (mapcat :in parts))
        (update-in (args-key)      concat* (mapcat :args parts))
        (assoc-in (query-key :where) (conj (->> parts
                                                (mapcat :where)
                                                (into '()))
                                           (symbol oper))))))

(defn- ensure-attr
  [{:keys [where] :as query} k arg-ident]
  (if (some #(= arg-ident (last %))
            where)
    query
    (update-in query (query-key :where) conj* ['?x (field-ref k) arg-ident])))

(defmulti apply-sort-segment
  (fn [_query seg]
    (when (vector? seg) :vector)))

(defmethod apply-sort-segment :default
  [query seg]
  (apply-sort-segment query [seg :asc]))

(defmethod apply-sort-segment :vector
  [query [k dir]]
  (let [arg-ident (arg-ident k)]
    (-> query
        (ensure-attr k arg-ident)
        (update-in [:find] conj* arg-ident)
        (update-in [:order-by] conj* [arg-ident dir]))))

(defn- apply-sort
  [query order-by]
  (reduce apply-sort-segment
          query
          (if (coll? order-by)
            order-by
            [order-by])))

(defn apply-options
  [query {:keys [limit offset order-by]} & {:as opts}]
  (with-options opts
    (cond-> query
      limit (assoc :limit limit)
      offset (assoc :offset offset)
      order-by (apply-sort order-by))))
