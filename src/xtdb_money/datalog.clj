(ns xtdb-money.datalog
  (:require [clojure.spec.alpha :as s]
            [clojure.pprint :refer [pprint]]))

(derive clojure.lang.PersistentArrayMap ::map)
(derive clojure.lang.PersistentVector ::vector)

(s/def ::args-key (s/coll-of keyword? :kind vector?))
(s/def ::query-prefix (s/coll-of keyword :kind vector?))
(s/def ::qualifier keyword?)
(defmulti options-spec type)
(defmethod options-spec ::map [_]
  (s/keys :req-un [::qualifier]
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

(defn- qualifier []
  (:qualifier *opts*))

(defn- query-key
  [& ks]
  (concat (:query-prefix *opts*) ks))

(defn- arg-ident
  ([k] (arg-ident k nil))
  ([k suffix]
  (symbol (str "?" (name k) suffix))))

(defn- field-ref
  [k & {explicit-type :qualifier}]
  (or ((:remap *opts*) k)
      (keyword (name (or explicit-type
                         (qualifier)))
               (name k))))

(defn- one?
  [x]
  (= 1 (count x)))

(def ^:private vec-of-one?
  (every-pred vector? one?))

(defn- extract-singular
  [x]
  (if (vec-of-one? x)
    (first x)
    x))

(defn- merge-and
  [w1 w2]
  (cond
    (and (vector? w1) (vector? w2))
    (vec (concat w1 w2))

    :else
    [(extract-singular w1) (extract-singular w2)]))

(defn- merge-or
  [w1 w2]
  (list 'or
        (extract-singular w1)
        (extract-singular w2)))

(def ^:private merge-where
  {:and merge-and
   :or merge-or})

(defn- merge-querylets
  "Returns a fn for merging querylets. Options are available for
  :in, :args, and :where and specify the function to use to combine
  the values in the two maps. The default function is concat."
  [oper]
  (fn [target source]
    (-> target
        (update-in [:in]    (fnil concat [])   (:in source))
        (update-in [:args]  (fnil concat [])   (:args source))
        (update-in [:where] (merge-where oper) (:where source)))))

(declare ->querylets)

(defmulti ->querylet
  "Accepts a criterion and returns a querylet map containing elements
  to be merged with :where, :args, and :in in the final
  datalog query."
  (fn [c]
    ; c will always be a vector, either a key-value pair
    ; or a construction like [:and crit1 crit2]
    (if (= 2 (count c)) ; this is a tuple key-value pair
      (let [[_k v] c]
        (cond
          (map? v) :compound
          (vector? v) (case (first v)
                        (:< :<= :> :>=) :comparison
                        :and            :intersection
                        :or             :union ; TODO: Do we ever hit this?
                        :=              :equality)))
      :conjunction)))

(defmethod ->querylet :default
  [[k v]]
  (let [arg-in (arg-ident k "-in")]
    {:where [['?x
              (field-ref k)
              arg-in]]
     :args [(coerce v)]
     :in [arg-in]}))

(defmethod ->querylet :conjunction
  [[oper & cs]]
  (->> cs
       (map #(reduce (merge-querylets :and)
                     (->querylets %)))
       (reduce (merge-querylets oper))))

(defmethod ->querylet :equality
  [[k [_op v]]]
  ; note the destructing is different from above, but the rest is the same
  (let [arg-in (arg-ident k "-in")]
    {:where [['?x
            (field-ref k)
            arg-in]]
   :args [(coerce v)]
   :in [arg-in]}))

(defmethod ->querylet :comparison
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
(defmethod ->querylet :intersection
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

(defmulti ^:private ->querylets
  "Accepts a criteria structure (map or vector) and returns
  a sequence of querylets"
  type)

(defmethod ->querylets ::map
  [c]
  (mapv ->querylet c))

(defmethod ->querylets ::vector
  [c]
  [(->querylet c)])

(defn- apply-querylet
  "Apply a querylet to a proper datalog query map"
  [query querylet]
  (-> query
      (update-in (args-key)         concat* (:args querylet))
      (update-in (query-key :in)    concat* (:in querylet))
      (update-in (query-key :where) concat* (:where querylet))))

(defmacro ^:private with-options
  [opts & body]
  `(binding [*opts* (merge *opts* ~opts)]
     ~@body))

(defn apply-criteria
  [query criteria opts]
  {:pre [(s/valid? ::options opts)]}

  (with-options opts
    (->> (->querylets criteria)
         (reduce (merge-querylets :and))
         (apply-querylet query))))

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
