(ns brimley.core
  "the two main things users do: load a menu edn and loop on it"
  (:require [brimley.actions   :as actions]
            [brimley.parse     :as parse]
            [brimley.tasks     :as tasks]
            [clojure.edn       :as edn]))

(defn read-file [path]
  (-> path slurp edn/read-string))

(defn load-menu!
  "load the menu specified in at `menu-src`
  (which can either be a file path or a clojure map)
  and set up the `ctx-atm` context atom with it."

  ([ctx-atm menu-src]
   (load-menu! ctx-atm menu-src tasks/detailed-prompt-tasks [:back-to-root!]))

  ([ctx-atm menu-src prompt-tasks after-tasks]

   (let [[common-entries choices]
         (cond-> menu-src
           (#{java.lang.String
              java.net.URL}
             (type menu-src)) read-file
           true               parse/process-menu)]
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
