(ns cyberme.psych.main
  (:require [cyberme.psych.widget :as w]
            [cyberme.psych.data :as d]
            [cyberme.psych.exp.guo :as g]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn data []
  #_(d/front-questions)
  (let [d (g/data)
        d2 (g/data-1)]
    (println "config:" @w/config)
    (println "total of exp:" (count d2))
    d2))

(defn root []
  [:div {:style {:position :absolute :top :0px :left :0px :right :0px :bottom :0px :background :white}}
   (r/with-let
     [dta (data)]
     (let [index @(rf/subscribe [:current-exp-index])]
       (-> dta (get index) :widget)))])

