(ns brimley.util-test
  (:require [clojure.test :refer :all]
            [brimley.util :as util]))

(deftest test-deref-and-validate
  (is (thrown? Exception (brimley.util/deref-and-validate (atom nil))))
  (is (thrown? Exception (brimley.util/deref-and-validate (atom 42))))
  (is (thrown? Exception (brimley.util/deref-and-validate (atom ""))))
  (is (thrown? Exception (brimley.util/deref-and-validate (atom {}))))
  (is (thrown? Exception (brimley.util/deref-and-validate (atom {:brimley {:choices []}}))))
  (is (thrown? Exception (brimley.util/deref-and-validate (atom {:brimley {:path []}}))))
  (let [expected-path []
        expected-choices
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
           :action      #'clojure.core/map?
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
         :path []}
        [choices path]
        (brimley.util/deref-and-validate
          (atom {:brimley
                 {:choices        expected-choices
                  :common-entries {""     #'brimley.tasks/show-choices,
                                   "\\"   #'brimley.tasks/back-one!,
                                   "\\\\" #'brimley.tasks/toggle-mode!},
                  :customizations {:prompt-tasks [:show-choices :show-detailed-path :show-detailed-prompt],
                                   :after-tasks  [:back-to-root!]},
                  :path           expected-path}}))]
    (is (= expected-path path))
    (is (= expected-choices choices))))
