(ns brimley.actions
  "ACTIONS are what the user chose to do from the current menu.
  these functions are for preparing the arguments for the action chosen by the user,
  and then executing the action with those arguments."
  (:require [brimley.tasks :as tasks]
            [brimley.util  :as util]))

(defn get-action
  "return the action corresponding to the user's choice
  given their current path into the menu"
  [ctx-atm choice]
  (let [[choices path] (util/deref-and-validate ctx-atm)]
    (get-in choices (conj path choice :action))))

(defmulti process-arg
  "gets the value intended to be passed as the real arg to the chosen action,
  based on the way the arg was declared in the menu edn file:
  - as-is: literally right out of the edn file
  - subst: whatever is found in the ctx-atm at the specified path, even if it's an atom
  - deref: same as subst but assume it is an atom that needs to be deref'd to become an arg to your action
  - dref2: same as deref but assume deref ended with a map, and get a value at a path inside it as the arg."
  (fn [ctx-atm arg]
    (cond
      (not (coll? arg)) :as-is
      (map? arg)        :map
      :else             (first arg))))

(defn process-arg-at-path [ctx-atm acc key-path]
  (update-in acc key-path (partial process-arg ctx-atm)))

(defmethod process-arg :default [ctx-atm arg]                                  arg)
(defmethod process-arg :as-is [ctx-atm arg]                                    arg)
(defmethod process-arg :subst [ctx-atm arg]              (get-in @ctx-atm(rest arg)))
(defmethod process-arg :deref [ctx-atm arg]        (deref(get-in @ctx-atm(rest arg))))
(defmethod process-arg :dref2 [ctx-atm arg] (get-in(deref(get-in @ctx-atm(nth  arg 1)))(nth arg 2)))
(defmethod process-arg :map   [ctx-atm arg]
  (reduce (partial process-arg-at-path ctx-atm)
          arg
          (util/keys-in arg)))

(defn get-args
  "return the args corresponding to the user's choice
  given their current path into the menu,
  and given how they were declared in the menu edn file"
  [ctx-atm choice]
  (let [[choices path] (util/deref-and-validate ctx-atm)
        raw-args       (get-in choices (conj path choice :args))]
    (map (partial process-arg ctx-atm) raw-args)))

(defn perform-action!
  "perform the action chosen by the user on args
  according to the action's args configuration
  and the current state of the context atom.
  if we're calling a user's function, put its result
  in the context atom"
  [ctx-atm user-choice]
  (if-let [common-choice (get-in @ctx-atm [:brimley :common-entries user-choice])]
    (common-choice ctx-atm)
    (let [action (get-action ctx-atm user-choice)
          args   (get-args   ctx-atm user-choice)]
      (if (string? action)
        (tasks/enter! action ctx-atm)
        (do (swap! ctx-atm assoc-in [:brimley :last-result]
                   (apply action args))
            (tasks/perform-after-tasks ctx-atm))))))
