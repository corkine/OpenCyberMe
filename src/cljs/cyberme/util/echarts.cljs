(ns cyberme.util.echarts
  (:require [react :as react]
            [reagent.core :as r]
            [reagent.dom :as d]
            [echarts :as echarts]
            ;["echarts/core" :as echarts]
            ;["echarts/charts" :refer (PieChart)]
            ;["echarts/components" :refer
            ; (TitleComponent
            ;   TooltipComponent
            ;   GridComponent
            ;   DatasetComponent
            ;   TransformComponent)]
            ;["echarts/features" :refer (LabelLayout UniversalTransition)]
            ;["echarts/renderers" :refer (SVGRenderer)]
            [echarts-liquidfill :as liquid]
            [goog.string :as gstring]
            [cljs-time.core :as t]
            [clojure.string :as str]
            [re-frame.core :as rf]))

;ref https://echarts.apache.org/handbook/zh/basics/import
;ref https://shadow-cljs.github.io/docs/UsersGuide.html#_using_npm_packages
#_(echarts/use [TitleComponent,
              TooltipComponent,
              GridComponent,
              DatasetComponent,
              TransformComponent,
              PieChart,
              LabelLayout,
              UniversalTransition,
              SVGRenderer])

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

(defonce echarts-instance (r/atom {}))

(defn render-chart [comp]
  (let [dom (d/dom-node comp)
        _ (when-let [exist-inst (get @echarts-instance dom)]
            (.dispose exist-inst)
            (swap! echarts-instance dissoc dom))
        chart (echarts/init dom)
        _ (swap! echarts-instance assoc dom chart)
        props (clj->js (:option (r/props comp)))
        _ (set! (.-onresize js/window)
                (clj->js (fn [_]
                           (when-let [chart-inst (get @echarts-instance dom)]
                             (.resize chart-inst)
                             (r/force-update comp)))))]
    (.setOption chart props)))

(defn rerender-chart [comp]
  (let [dom (d/dom-node comp)
        _ (when-let [exist-inst (get @echarts-instance dom)]
            (.dispose exist-inst)
            (swap! echarts-instance dissoc dom))
        chart (echarts/init dom)
        _ (swap! echarts-instance assoc dom chart)
        props (clj->js (:option (r/props comp)))]
    (.setOption chart props)))

(defn dispose-charts [comp]
  (let [dom (d/dom-node comp)]
    (when-let [exist-inst (get @echarts-instance dom)]
      (swap! echarts-instance dissoc dom)
      (.dispose exist-inst))))

(defn EChartsM [_]
  (r/create-class
    {:component-did-mount    render-chart
     :component-did-update   rerender-chart
     :component-will-unmount dispose-charts
     :reagent-render         (fn [config]
                               [:div {:style (or (:style config)
                                                 {:width  "100px"
                                                  :height "100px"})}])}))

(defn EChartsR [options]
  (r/as-element
    (let [mychart (react/useRef nil)]
      (react/useEffect (fn []
                         (let [chart (echarts/init (.-current mychart))]
                           (set! (.-chart js/document) chart)
                           (set! (.-onresize js/window)
                                 (clj->js (fn [_] (.resize chart)))))
                         (.setOption (.-chart js/document) (.-option options)))
                       (clj->js [options js/ResizeObserver]))
      [:div {:ref   mychart
             :style (.-style options)}])))