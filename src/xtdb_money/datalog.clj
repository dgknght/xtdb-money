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

(defn- dispatch-criterion
  [[_k v]]
  (cond
    (map? v) :compound
    (vector? v) (case (first v)
                  (:< :<= :> :>=) :comparison
                  :and            :intersection
                  :or             :union
                  :=              :equality)))

(defmulti apply-criterion
  (fn [_query criterion]
    (dispatch-criterion criterion)))

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

; This still isn't the right abstraction. I also need to be able
; to collect the values to be placed in the :args list
; and the :in list
; Also, it didn't work for the intersection that looks like:
; {:field [:and [:>= 1] [:<= 5]]}
(defmulti ->where dispatch-criterion)

(defmethod ->where :default
  [[k _v]]
  ['?x
   (field-ref k)
   (arg-ident k "-in")])

(defmethod ->where :comparison
  [[k [oper _v]]]
  (let [arg (arg-ident k)]
    [['?x
      (field-ref k)
      arg]
     [(list (symbol (name oper))
            arg
            (arg-ident k "-in"))]]))

#_(defmethod ->where :intersection
  [[k [_and & vs]]]
  (let [attr-ref (arg-ident k)
        input-refs (map (comp symbol
                              #(str "?" (name k) "-in-" %)
                              #(+ 1 %))
                        (range (count vs)))]
    (cons
      ['?x (field-ref k) attr-ref]
      (->> vs
           (interleave input-refs)
           (partition 2)
           (map (fn [[input-ref [oper]]]
                  [(list (-> oper name symbol)
                         attr-ref
                         input-ref)]))))))

(defmethod ->where :union
  [[k criterion]]
  )

(defn- simple-match
  [query [k v]]
  (let [input (arg-ident k "-in")]
    (-> query
        (update-in (query-key :where)
                   conj*
                   ['?x (field-ref k) input])
        (update-in (query-key :in) conj* input)
        (update-in (args-key) conj* (coerce v)))))

(defmethod apply-criterion :default
  [query tuple]
  (simple-match query tuple))

(defmethod apply-criterion :equality
  [query [k [_ v]]]
  (simple-match query [k v]))

(defmethod apply-criterion :comparison
  [query [k [_oper v] :as criterion]]
  (let [input (arg-ident k "-in")]
    (-> query
        (update-in (args-key) conj* (coerce v))
        (update-in (query-key :in) conj* input)
        (update-in (query-key :where)
                   concat*
                   (->where criterion)))))

(defmethod apply-criterion :intersection
  [query [k [_and & vs :as criterion]]]
  ; 1. establish a reference to the model attribute
  ; 2. apply each comparison to the reference
  ; 3. decide if we need to wrap with (and ...)
  (let [attr (name k)
        attr-ref (arg-ident k)
        input-refs (map (comp symbol
                              #(str "?" attr "-in-" %)
                              #(+ 1 %))
                        (range (count vs)))]
    (-> query
        (update-in (args-key)           concat* (map (comp coerce last) vs))
        (update-in (query-key :in)      concat* input-refs)
        (update-in (query-key :where)   concat* (cons
                                                  ['?x (field-ref k) attr-ref]
                                                  (->> vs
                                                       (interleave input-refs)
                                                       (partition 2)
                                                       (map (fn [[input-ref [oper]]]
                                                              [(list (-> oper name symbol)
                                                                     attr-ref
                                                                     input-ref)]))))))))

(defn- apply-key-value-criterion
  [input k1 q [k2 v]]
  (-> q
      (update-in (query-key :where)
                 conj*
                 [input
                  (field-ref k2 :model-type k1)
                  (arg-ident k2 "-in")])
      (update-in (query-key :in) conj* (arg-ident k2 "-in"))
      (update-in (args-key) conj* (coerce v))))

; NB This should probably be recursive
(defmethod apply-criterion :compound
  [query [k1 m]] ; TODO: Maybe this could also be a vector instead of a map?
  (let [input (arg-ident k1)]
    (reduce (partial apply-key-value-criterion input k1)
            (-> query
                (update-in (query-key :where)
                           conj*
                           ['?x
                            (field-ref k1)
                            input]))
            m)))

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

  (-> query
      (assoc-in (query-key :where)
                (conj
                  (->> criterias
                       (mapcat (fn [criteria]
                                 (with-options opts criteria
                                   (mapv (fn [[k _v]] ; TODO: is this always a key-value pair?
                                           ; TODO: v above needs to be written into args-key
                                           ['?x (field-ref k) (arg-ident k)])
                                         criteria))))
                       (into '()))
                  (symbol oper)))
      #_(update-in (query-key :in) conj* (arg-ident k2 "-in"))
      #_(update-in (args-key) conj* (coerce v))))

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
