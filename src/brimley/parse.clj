(ns brimley.parse
  (:require [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            [brimley.util :as util]))

;; these three specs relate to `actions/process-arg`
(s/def ::subst (s/and vector?
                      #(> (count %) 1)
                      #(= :subst (first %))))

(s/def ::deref (s/and vector?
                      #(> (count %) 1)
                      #(= :deref (first %))))

(s/def ::dref2 (s/and vector?
                      #(= (count %) 3)
                      #(= :dref2 (first %))
                      #(vector? (nth % 1))
                      #(vector? (nth % 2))
                      #(>= (count(nth % 1)) 1)
                      #(>= (count(nth % 2)) 1)))

;; brimley can still pass regular vectors to user functions
;; but they can't start with any of the three keywords we use:
(s/def ::regular-vec (s/and vector?
                            #(not(#{:subst :deref :dref2} (first %)))))

;; and brimley can pass things that aren't vectors to user functions:
(s/def ::non-vector #(not(vector? %)))

;; so these are the kinds of args that brimley can pass (except for maps)
(s/def ::leaf-arg
  (s/or :subst          ::subst
        :deref          ::deref
        :dref2          ::dref2
        :regular-vector ::regular-vec
        :non-vector     ::non-vector))

;; and when brimley passes a map to a user function, every leaf in that map
;; should conform to the `::leaf-arg` spec
(s/def ::map-arg (s/and map?
                        #(s/valid? (s/coll-of ::leaf-arg) (util/leaves-in %))))

;; so these are the kinds of args that brimley can pass _period_
(s/def ::arg (s/or :map-arg  ::map-arg
                   :leaf-arg ::leaf-arg))

;; if a choice is just a symbol, then it is an always-available not-displayed
;; option that calls a function with the context atom:
(s/def ::choice-always symbol?)

;; if a choice should call a user function, then the choice is a description +
;; the user function's symbol + an argument list where each arg conforms to `::arg`
(s/def ::choice-action (s/and vector?
                              #(= (count %) 3)
                              #(string? (nth % 0))
                              #(symbol? (nth % 1))
                              #(s/valid? (s/coll-of ::arg) (nth % 2))))

;; there are three kinds of choices:
(s/def ::option
  (s/or :always  symbol?
        :action  ::choice-action
        :submenu ::menu))

(defn every-odd [coll] ;; colloquially speaking, i.e. first in a coll is #1
  (map second (filter #(even? (first %)) (map-indexed vector coll))))

(defn every-even [coll]
  (map second (remove #(even? (first %)) (map-indexed vector coll))))

;; a menu alternates between the string the user would type and the option it maps to:
(s/def ::menu
  (s/and vector?
         #(even? (count %))
         #(s/valid? (s/coll-of string?) (every-odd %))
         #(s/valid? (s/coll-of ::option) (every-even %))))

(defmulti parse-menu-item
  "parses an abbreviation k and its value from the menu edn file
  where the value could be a normal action, a sub-menu, or one of the
  always-available invisible common choices (e.g. for exiting a sub-menu)"
  (fn [common-choices [k v]]
    (when (contains? @common-choices k)
      (throw (ex-info (str "Reserved menu choice: " k) {:key k, :val v})))
    (if (symbol? v)
      1
      (count v))))

(defn gather-menu-pairs [common-choices acc pair]
  (if-let [[k v] (parse-menu-item common-choices pair)]
    (conj acc k v)
    acc))

(defn to-ordered-choices
  "requires pairs be a result of a partition 2"
  [common-choices pairs]
  (->> pairs
       (reduce (partial gather-menu-pairs common-choices) [])
       (apply array-map)))

(defmethod parse-menu-item 3 ;; :actions are name + function + arglist = 3
  [common-choices [k v]]
  (if-let [sym (second v)]
    (if-let [function (ns-resolve *ns* sym)]
      [k {:description (first v)
          :action      function
          :args        (nth v 2)}]
      (throw (ex-info (str "Cannot resolve symbol: " sym) {:key k, :val v, :symbol sym})))
    (throw (ex-info (str "No symbol at key: " k) {:key k, :val v}))))

(defmethod parse-menu-item 2 ;; sub-menu are name + choices = 2
  [common-choices [k v]]
  [k (assoc (to-ordered-choices common-choices (partition 2 (second v)))
            :description (first v)
            :action      k)])

(defmethod parse-menu-item 1 ;; a common choice is a function(ctx-atm) = 1
  [common-choices [k v]]
  (swap! common-choices assoc k (ns-resolve *ns* v))
  nil)

(defn parse-menu-edn
  "parses a menu edn file and returns the always-available invisible choices
  and the normal choices"
  [f]
  (let [common-choices (atom {})
        data           (->> f slurp edn/read-string)]
    (if (s/valid? ::menu data)
      (let [choices (->> data
                         (partition 2)
                         (to-ordered-choices common-choices))]
        [@common-choices choices])
      (let [explanation (with-out-str(s/explain ::menu data))]
        (println explanation)
        (flush)
        (throw(ex-info "brimley: contents of file did not conform to spec"
                       {:file        f
                        :explanation explanation}))))))
