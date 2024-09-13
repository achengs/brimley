(ns brimley.path
  "print-related code for the user's current path in the menu"
  (:require [clojure.string :as str]
            [brimley.util      :as util]))

(defn collect-directions
  "adds to `acc` (a list of directions for getting to each turn)
  one more to get to the next turn `x`"
  [acc x]
  (conj acc
        (conj (vec (last acc))
              x)))

(defn directions
  "takes the directions for a final destination
  and returns a list of directions for getting to each turn"
  [turns]
  (reduce collect-directions [] turns))

(defmulti get-path-component
  "return the string to represent a single particular path turn."
  (fn [ctx-atm _]
    (get-in @ctx-atm [:brimley :customizations :path-component])))

(defmethod get-path-component :default
  [ctx-atm path]
  (get-in @ctx-atm
          (sequence cat
                    [[:brimley :choices]
                     path
                     [:description]])))

(defmulti format-path
  "turns the current path into a formatted string"
  (fn [ctx-atm _]
    (get-in @ctx-atm [:brimley :customizations :format-path])))

(defmethod format-path :default
  [ctx-atm v]
  (str "/" (str/join "/" v)))

(defmulti path->string
  "turn user's current path into a string"
  (fn [ctx-atm]
    (get-in @ctx-atm [:brimley :customizations :path->string])))

(defmethod path->string :default
  [ctx-atm]
  (let [[choices path] (util/deref-and-validate ctx-atm)]
    (->> path
         directions
         (map (partial get-path-component ctx-atm))
         (format-path ctx-atm))))
