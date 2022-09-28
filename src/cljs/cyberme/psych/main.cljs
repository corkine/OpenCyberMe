(ns cyberme.psych.main
  (:require [cyberme.psych.widget :as w]
            [cyberme.psych.data :as d]
            [cyberme.psych.exp.guo :as g]
            [cyberme.psych.exp.jiang :as j]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn data []
  (let [exp-id @w/exp-id
        d (cond (= "1001" exp-id) (g/data)
                (= "1002" exp-id) (j/data)
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

