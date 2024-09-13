(ns brimley.actions-test
  (:require [clojure.test :refer :all]
            [brimley.actions :as actions]))

(def args (atom []))

(defn remember-args [& arguments]
  (reset! args arguments))

(defn each-fixture [f]
  (reset! args [])
  (f)
  (reset! args []))


(def menu {:brimley
           {:choices
            {"n"
             {:description "Specify name",
              :action      #'clojure.core/map
              :args        [[:subst :state]]},
             "a"
             {:description "Specify age",
              :action      #'clojure.core/macroexpand
              :args        [[:subst :state]]},
             "env"
             {"d"
              {:description "use dev",
               :action      #'clojure.core/mapv
               :args        ["dev" [:subst :env]]},
              "p"
              {:description "use prod",
               :action      #'clojure.core/map-indexed
               :args        ["prod" [:subst :env]]},
              "."
              {:description "show",
               :action      #'clojure.core/mapcat
               :args        [[:deref :env]]},
              :description "Environment",
              :action      "env"},
             "s"
             {:description "Show everything",
              :action      #'brimley.actions-test/remember-args
              :args
              ["the first argument configured to be passed to show-everything"
               [:deref :env]
               {:literal 42,
                :last    [:subst :brimley :last-result],
                :person
                {:name [:dref2 [:state] [:name]], :age [:dref2 [:state] [:age]]}}]}},
            :common-entries
            {""     #'brimley.tasks/show-choices,
             "\\"   #'brimley.tasks/back-one!,
             "\\\\" #'brimley.tasks/toggle-mode!},
            :customizations
            {:prompt-tasks [:show-choices :show-detailed-path :show-detailed-prompt],
             :after-tasks  [:back-to-root!]},
            :path []}})

(deftest get-action
  (is (= #'clojure.core/map
         (actions/get-action (atom menu) "n"))
      "given user is at root and a valid choice, find the right action")
  (is (= #'clojure.core/map-indexed
         (actions/get-action (atom (assoc-in menu [:brimley :path] ["env"])) "p"))
      "given in a submenu and a valid choice, find the right action")
  (is (nil? (actions/get-action (atom (assoc-in menu [:brimley :path] ["jack"])) "n"))
      "given an invalid path, return nil")
  (is (nil? (actions/get-action (atom (assoc-in menu [:brimley :path] ["env"])) "n"))
      "given an invalid choice, return nil"))


(deftest process-arg
  ;; verify ability to pass literal arguments
  (is(= nil          (actions/process-arg (atom menu) nil)))
  (is(= "string"     (actions/process-arg (atom menu) "string")))
  (is(= :keyword     (actions/process-arg (atom menu) :keyword)))
  (is(= 0            (actions/process-arg (atom menu) 0)))
  (is(= 42           (actions/process-arg (atom menu) 42)))
  (is(= [1 2 3]      (actions/process-arg (atom menu) [1 2 3])))
  (is(= (list 1 2 3) (actions/process-arg (atom menu) (list 1 2 3))))
  (is(= #{4 5 6}     (actions/process-arg (atom menu) #{4 5 6})))
  (is(= {:a 2}       (actions/process-arg (atom menu) {:a 2})))

  (is(= [:back-to-root!]
        (actions/process-arg
          (atom menu)
          [:subst :brimley :customizations :after-tasks]))
     "can substitute a value from the context atom")

  (is(= :inner-value
        (actions/process-arg
          (atom (assoc-in menu
                          [:aa :bb :cc]
                          (atom :inner-value)))
          [:deref :aa :bb :cc]))
     "can go to a path in the context atom and deref before substituting")

  (is(= :value-at-path
        (actions/process-arg
          (atom (assoc-in menu
                          [:dd :ee :ff]
                          (atom {:g {:h {:i :value-at-path
                                         :j :other}
                                     :k :another}})))
          [:dref2 [:dd :ee :ff] [:g :h :i]]))
     "can go to a path in the context atom, deref, and go to a second path before substituting")

  (is(= {:a 5
         :b {:c :value-at-path}}
        (actions/process-arg
          (atom (assoc-in menu
                          [:dd :ee :ff]
                          (atom {:g {:h {:i :value-at-path
                                         :j :other}
                                     :k :another}})))
          {:a 5
           :b {:c [:dref2 [:dd :ee :ff] [:g :h :i]]}}))
     "can do the same inside maps"))

(deftest perform-action
  (actions/perform-action!
    (-> menu
        (assoc-in [:env] (atom "uat"))
        (assoc-in [:brimley :last-result] 43)
        (assoc-in [:state] (atom {:name "bob",
                                  :age  22}))
        atom)
    "s")
  (is(= ["the first argument configured to be passed to show-everything"
         "uat"
         {:literal 42,
          :last    43,
          :person
          {:name "bob"
           :age  22}}]
        @args)
     "we call the right function with the right args"))

(use-fixtures :each each-fixture)
