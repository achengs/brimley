(ns brimley.path-test
  (:require [clojure.test :refer :all]
            [brimley.path :as path]))

(def menu
  {:brimley
   {:choices
    {"a"
     {:description "Specify age",
      :action      #'clojure.core/macroexpand
      :args        [[:subst :state]]},
     "s"
     {:description "some"
      :action      "s"
      "t"
      {:description "test path",
       :action      "t"
       "p"
       {:description "!",
        :action      #'clojure.core/map-indexed
        :args        ["prod" [:subst :env]]}}},
     "S"
     {:description "Show everything",
      :action      #'brimley.actions-test/remember-args
      :args
      []}}
    :path ["s" "t" "p"]}})

(deftest path->string
  (is(= "/some/test path/!"
        (path/path->string (atom menu)))))
