(ns cyberme.psych.main
  (:require [cyberme.psych.widget :as w]
            [cyberme.psych.data :as d]
            [cyberme.psych.exp.guo :as g]
            [cyberme.psych.exp.jiang :as j]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.string :as str]))

(defn hint
  ([title msg]
   {:type   :hint
    :widget [w/hint title msg "开始" w/go-next]})
  ([msg] (hint "" msg)))

(defn gist-dispatch [gist-id]
  (cond (= gist-id "jiang-demo")
        (let [data (first (j/problem-data))]
          (vec (flatten
                 [
                  (hint "条件1练习")
                  (j/problem-demo 1)
                  (hint "条件2练习")
                  (j/problem-demo 2)
                  (hint "条件3练习")
                  (j/problem-demo 3)
                  (hint "条件4练习")
                  (j/problem-demo 4)
                  (hint "条件1")
                  (j/problem 4 data)
                  (hint "条件2")
                  (j/problem 2 data)
                  (hint "条件3")
                  (j/problem 3 data)
                  (hint "条件4")
                  (j/problem 4 data)
                 ])))
        (= gist-id "guo-demo")
        (let [data (first (g/step23))]
          (vec (flatten
                 [(hint "条件1练习")
                  (g/problem-demo 1)
                  (hint "条件2练习")
                  (g/problem-demo 2)
                  (hint "条件3练习")
                  (g/problem-demo 3)
                  (hint "条件4练习")
                  (g/problem-demo 4)
                  (hint "条件1")
                  (g/problem 1 data)
                  (hint "条件2")
                  (g/problem 2 data)
                  (hint "条件3")
                  (g/problem 3 data)
                  (hint "条件4")
                  (g/problem 4 data)])))
        :else
        [{:type   :hint
          :widget [w/hint "CyberMe Psychology System"
                   "欢迎使用实验系统，请选择要进行的实验。"]}]))

(defn data []
  (let [exp-id (or @w/exp-id "")
        d (cond (= "1001" exp-id) (g/data)
                (= "1002" exp-id) (j/data)
                (str/starts-with? exp-id "gist") (gist-dispatch (str/replace-first exp-id "gist-" ""))
                :else [{:type   :hint
                        :widget [w/hint "CyberMe Psychology System"
                                 "欢迎使用实验系统，请选择要进行的实验。"]}])]
    (println "config:" @w/config)
    (println "total of exp:" (count d))
    d))

(defn root []
  [:div {:style {:position :absolute :top :0px :left :0px :right :0px :bottom :0px :background :white}}
   (r/with-let
     [dta (data)]
     (let [index @(rf/subscribe [:current-exp-index])]
       (-> dta (get index) :widget)))])

