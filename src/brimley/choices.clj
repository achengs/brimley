(ns brimley.choices
  "print-related code for choices (menu items that the user could choose at their current path)"
  (:require [clojure.pprint :as pprint]
            [clojure.string :as str]
            [brimley.path      :as path]
            [brimley.util      :as util]))

(defmulti format-submenu
  "given the ctx-atm, formats the entry-name as a sub-menu and returns the string"
  (fn [ctx-atm entry-name]
    (get-in @ctx-atm [:brimley :customizations :format-submenu])))

(defmethod format-submenu :default
  [ctx-atm entry-name]
  (format "%s (sub-menu)" entry-name))

(defmethod format-submenu :submenu-elipsis
  [ctx-atm entry-name]
  (format "%s ..." entry-name))

(defn get-display-info-as-map
  "take a kv pair from a menu and return a map containing the info needed to
  display it as a menu choice. if it's a choice to enter a sub-menu,
  that will be indicated by its resulting name."
  [ctx-atm [k m]]
  {"o" k
   "d" (if (contains? m :args)
         (:description m)
         (format-submenu ctx-atm (:description m)))})

(defn strip-margins
  "because we used print-table and don't need the left and right margins"
  [s]
  (second (re-matches #"\| (.*) \|" s)))

(defmulti format-choices
  "takes the current ctx-atm and formats the currently available choices
  except the always-available invisible common choices from the parsed menu edn
  (if any) as a single string"
  (fn [ctx-atm]
    (get-in @ctx-atm [:brimley :customizations :format-choices])))

(defmethod format-choices :default
  [ctx-atm]
  (let [[choices path] (util/deref-and-validate ctx-atm)]
    (->> (dissoc (get-in choices path) :action :description)
         (map (partial get-display-info-as-map ctx-atm))
         (pprint/print-table ["o" "d"])
         with-out-str
         str/split-lines
         (drop 3)
         (map strip-margins)
         (str/join "\n")
         (str "\n"))))
