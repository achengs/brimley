(ns brimley.tasks
  "TASKS are menu-related things that need to happen
  e.g. on every prompt or after every action or to enter/exit sub-menus"
  (:require [clojure.set    :as set]
            [clojure.string :as str]
            [brimley.choices   :as choices]
            [brimley.path      :as path]
            [brimley.util      :as util]))

(defn back-to-root!
  "put user back at the top level of the menu"
  [ctx-atm]
  (swap! ctx-atm assoc-in [:brimley :path] []))

(defn exit-to-parent-menu
  "return the new path we should have after exiting the current sub-menu
  if we are in a sub-menu"
  [path]
  (if (empty? path)
    (do (println "\nAlready at the top.")
        (flush)
        path)
    (do (println "\nExiting to parent menu.")
        (flush)
        (into [] (butlast path)))))

(defn back-one!
  "update the given context atom with exiting the current sub-menu"
  [ctx-atm]
  (swap! ctx-atm update-in [:brimley :path] exit-to-parent-menu))

(defn enter!
  "update the given context atom with entering the indicated sub-menu"
  [option ctx-atm]
  (swap! ctx-atm update-in [:brimley :path] conj option))

(defn show-choices [ctx-atm]
  (println (choices/format-choices ctx-atm))
  (flush))

(defn show-path [ctx-atm]
  (print (path/path->string ctx-atm))
  (flush))

(defn show-detailed-path [ctx-atm]
  (printf "Your current menu path:\n%s\n"
          (path/path->string ctx-atm))
  (flush))

(defn show-prompt [ctx-atm]
  (print ": ")
  (flush))

(defn show-detailed-prompt [ctx-atm]
  (print "To quit, Control-D in a terminal or ESC in a REPL. Your choice: ")
  (flush))

(defn line-break [ctx-atm]
  (println)
  (flush))

(def keyword->task
  {:back-to-root!        back-to-root!
   :line-break           line-break
   :show-choices         show-choices
   :show-path            show-path
   :show-prompt          show-prompt
   :show-detailed-path   show-detailed-path
   :show-detailed-prompt show-detailed-prompt})

(def detailed-prompt-tasks
  [:show-choices
   :show-detailed-path
   :show-detailed-prompt])

(defn perform-task [ctx-atm t]
  (if (keyword? t)
    ((keyword->task t) ctx-atm)
    (apply t ctx-atm)))

(defn expert-mode! [ctx-atm]
  (swap! ctx-atm assoc-in
         [:brimley :customizations :prompt-tasks]
         [:line-break :show-path :show-prompt])
  (swap! ctx-atm update-in
         [:brimley :customizations]
         assoc :format-submenu :submenu-elipsis))

(defn novice-mode! [ctx-atm]
  (swap! ctx-atm assoc-in
         [:brimley :customizations :prompt-tasks]
         detailed-prompt-tasks)
  (swap! ctx-atm update-in
         [:brimley :customizations]
         dissoc :format-submenu))

(defn expert-path->string [path]
  (str "/" (str/join "/" path)))

(defn expert-paths! [ctx-atm]
  (swap! ctx-atm assoc-in
         [:brimley :customizations :path->string]
         expert-path->string))

(defn novice-paths! [ctx-atm]
  (swap! ctx-atm update-in
         [:brimley :customizations]
         dissoc :path->string))

(defn toggle-mode! [ctx-atm]
  (if (= detailed-prompt-tasks
         (get-in @ctx-atm [:brimley :customizations :prompt-tasks]))
    (expert-mode! ctx-atm)
    (novice-mode! ctx-atm)))

(defn perform-after-tasks [ctx-atm]
  (run! (partial perform-task ctx-atm)
        (get-in @ctx-atm [:brimley :customizations :after-tasks])))

(defn valid-options-set
  "returns the SET of valid possible responses from the user given their current path,
  including the responses that are available at every path (if any)"
  [ctx-atm]
  (let [[choices path] (util/deref-and-validate ctx-atm)
        common-entries (-> (get-in @ctx-atm [:brimley :common-entries]) keys set)]
    (-> (get-in choices path)
        keys set
        (set/union common-entries))))

(defn show-user-choice-prompt
  "run all the configured prompt-related tasks in sequence,
  to prompt the user for a choice."
  [ctx-atm]
  (run! (partial perform-task ctx-atm)
        (get-in @ctx-atm [:brimley :customizations :prompt-tasks])))

(defn get-user-choice
  "get a valid choice from the user given the current path into the menu and the
  state of everything in the context atom"
  [ctx-atm]
  (let [valid-options (valid-options-set ctx-atm)]
    (show-user-choice-prompt ctx-atm)
    (loop [user-choice (read-line)]
      (when (some? user-choice)
        (if (contains? valid-options user-choice)
          (do (println)
              (flush)
              user-choice)
          (do (printf "[%s] is not a valid option. Your choice: " user-choice)
              (flush)
              (recur (read-line))))))))
