(ns cyberme.psych.main
  (:require [cyberme.psych.widget :as w]
            [cyberme.psych.data :as d]
            [cyberme.psych.exp.guo :as g]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(def data
  #_(d/front-questions)
  (g/data))

(defn root []
  [:div {:style {:position :absolute :top :0px :left :0px :right :0px :bottom :0px :background :white}}
   (r/with-let
     []
     (let [index @(rf/subscribe [:current-exp-index])]
       (-> data (get index) :widget)))])

