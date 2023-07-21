(ns xtdb-money.accounts
  #_(:require #?(:clj [clojure.pprint :refer [pprint]]
               :cljs [cljs.pprint :refer [pprint]])
              [cljs.core :as c]))

(defn left-side?
  [{:keys [type]}]
  (#{:asset :expense} type))

(defn- polarizer
  [action account]
  (if (left-side? account)
    (if (= :credit action) -1M 1M)
    (if (= :credit action) 1M -1M)))

(defn polarize
  ([{:keys [quantity action account]}]
   (polarize quantity action account))
  ([quantity action account]
   {:pre [quantity
          (#{:debit :credit} action)
          (:type account)]}
   (* quantity (polarizer action account))))

(defn- assoc-children
  ([account by-parent opts] (assoc-children account by-parent opts 0))
  ([{:keys [id path] :as account} by-parent {:keys [sort-fn] :as opts} depth]
   (assoc account
          :depth depth
          :children
          (->> (by-parent id)
               (sort-by sort-fn)
               (mapv (comp #(assoc %
                                   :path (conj path (:name %))
                                   :parent account)
                           #(assoc-children % by-parent opts (inc depth))))))))

(defn nest
  ([accounts] (nest accounts {:sort-fn :name}))
  ([accounts {:keys [sort-fn] :as opts}]
  (let [by-parent (->> accounts
                       (filter :parent-id)
                       (group-by :parent-id))]
    (->> accounts
         (remove :parent-id)
         (map #(assoc % :path [(:name %)]))
         (sort-by sort-fn)
         (map #(assoc-children % by-parent opts))))))


(defn- unnest-account
  [{:keys [children] :as account}]
  (lazy-seq (cons account (mapcat unnest-account children))))

(defn unnest
  [accounts]
  (mapcat unnest-account accounts))
