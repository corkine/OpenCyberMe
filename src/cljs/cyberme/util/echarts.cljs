(ns cyberme.util.echarts
  (:require [react :as react]
            [reagent.core :as r]
            [echarts :as echarts]
            [goog.string :as gstring]
            [cljs-time.core :as t]
            [clojure.string :as str]
            [re-frame.core :as rf]))

(defn ECharts [options]
  (r/as-element
    (let [mychart (react/useRef nil)]
      (react/useEffect (fn []
                         (set! (.-chart js/document)
                               (echarts/init (.-current mychart) #_(.-theme options)))
                         (.setOption (.-chart js/document) (.-option options)))
                       (clj->js [options js/ResizeObserver]))
      [:div {:ref   mychart
             :style (.-style options)}])))