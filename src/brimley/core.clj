(ns brimley.core
  "the two main things users do: load a menu edn and loop on it"
  (:require [brimley.actions   :as actions]
            [brimley.parse     :as parse]
            [brimley.tasks     :as tasks]))

(defn load-menu!
  "load the menu specified in the edn file at `menu-edn-path`
  and set up the `ctx-atm` context atom with it."

  ([ctx-atm menu-edn-path]
   (load-menu! ctx-atm menu-edn-path tasks/detailed-prompt-tasks [:back-to-root!]))

  ([ctx-atm menu-edn-path prompt-tasks after-tasks]

   (let [[common-entries choices] (parse/parse-menu-edn menu-edn-path)]
     (swap! ctx-atm assoc-in [:brimley :choices] choices)
     (swap! ctx-atm assoc-in [:brimley :common-entries] common-entries))

   (swap! ctx-atm assoc-in
          [:brimley :customizations :prompt-tasks]
          prompt-tasks)

   (swap! ctx-atm assoc-in
          [:brimley :customizations :after-tasks]
          after-tasks)

   (tasks/back-to-root! ctx-atm)))

(defn loop-menu!
  "loop between the user picking options and us performing the chosen action."
  [ctx-atm]
  (loop [user-choice (tasks/get-user-choice ctx-atm)]
    (when (some? user-choice)
      (actions/perform-action! ctx-atm user-choice)
      (recur (tasks/get-user-choice ctx-atm)))))
