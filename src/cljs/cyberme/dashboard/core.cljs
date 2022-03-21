(ns cyberme.dashboard.core
  (:require [re-frame.core :as rf]
            [clojure.string :as str]
            [cljs-time.format :as format]
            [cljs-time.core :as t]
            [cyberme.util.echarts :refer [ECharts]]))

(def today (format/unparse-local
             (format/formatter "yyyy-MM-dd")
             (t/time-now)))

(def yesterday (format/unparse-local
                 (format/formatter "yyyy-MM-dd")
                 (t/plus (t/time-now) (t/period :days 1))))

(def month-days (t/number-of-days-in-the-month (t/time-now)))

(def max-word 50)

(defn chart-1 [{:keys [title value width height start stop]
                :or   {width "200px" height "200px"
                       title "未命名" value 0.8
                       start "#2fdb9a" stop "#1cbab4"}}]
  [:> ECharts
   {:style {:width width :height height}
    :option
    {:title  {:text      title
              :x         "center"
              :y         "38.5%"
              :zlevel    3
              :textStyle {:fontWeight "700"
                          :color      "#303030"
                          :fontSize   "20"}}
     :legend {:show false}
     :series [{:type           "pie"
               :zlevel         2
               :radius         ["0" "10%"]
               :center         ["50%" "45%"]
               :clockWise      false
               :animation      false
               :itemStyle      {:normal     {:shadowBlur  0
                                             :shadowColor ""
                                             :color       "#fff"
                                             :label       {:show false}
                                             :labelLine   {:show false}}
                                :borderCap  "round"
                                :borderJoin "round"}
               :hoverAnimation false
               :data           [100]}
              {:type           "pie"
               :clockWise      false
               :radius         ["40%" "62%"]
               :center         ["50%" "45%"]
               :itemStyle      {:normal {:label     {:show false}
                                         :labelLine {:show false}}}
               :hoverAnimation false
               :data           [{:value     (* value 100)
                                 :name      title
                                 :itemStyle {:normal {:color
                                                      {:type "linear"
                                                       :x    0
                                                       :y    0
                                                       :x2   1
                                                       :y2   1
                                                       :colorStops
                                                       [{:offset 0
                                                         :color  start}
                                                        {:offset 1
                                                         :color  stop}]}
                                                      :borderRadius ["10%" "10%"]}}}
                                {:value     (- 100 (* value 100))
                                 :name      "Other"
                                 :itemStyle {:normal {:color
                                                      {:colorStops
                                                       [{:offset 0
                                                         :color  "#F7F7F7"}
                                                        {:offset 1
                                                         :color  "#F7F7F7"}]}
                                                      :borderRadius ["10%" "10%"]}}}]}
              {:type           "pie"
               :clockWise      false
               :radius         ["62%" "70%"]
               :center         ["50%" "45%"]
               :itemStyle      {:normal {:borderCap   "round"
                                         :borderJoin  "round"
                                         :shadowBlur  0
                                         :shadowColor "rgba(0,0,0,.2)"
                                         :color       "#fff"
                                         :label       {:show false}
                                         :labelLine   {:show false}}}
               :hoverAnimation false
               :data           [100]}]}
    }])

(def goal-active 600)

(defn dashboard-page []
  (let [recent @(rf/subscribe [:dashboard/recent-data])
        {:keys [todo fitness blue clean express movie]} (:data recent)
        {:keys [active rest]} fitness
        {:keys [MorningBrushTeeth MorningCleanFace
                NightCleanFace NightBrushTeeth]} clean
        ;;CLEAN
        clean-count (+ (if MorningBrushTeeth 1 0)
                       (if MorningCleanFace 1 0)
                       (if NightCleanFace 1 0)
                       (if NightBrushTeeth 1 0))
        ;;EXPRESS
        express (filterv #(not= (:status %) 0) express)
        ;;BLUE
        {:keys [MonthBlueCount]} blue
        non-blue-percent (- 1 (/ MonthBlueCount month-days))
        ;;MOVIE
        movie (reverse (sort :last_update movie))
        ;;TO-DO
        today-todo (get todo (keyword today) [])
        not-finished (count (filter #(not= (:status %) "completed") today-todo))
        finish-percent (if (= (count today-todo) 0)
                         1 (- 1 (/ not-finished (count today-todo))))
        days (reverse (sort (keys todo)))]
    [:div.container
     [:div.columns
      [:div.column.pr-0
       [:div.mx-2.mt-3.box
        [:p [:span.is-size-5.is-family-code.has-text-weight-bold.is-unselectable
             "> " today]]
        [:div.is-flex {:style {:flex-wrap :wrap}}
         [:div {:style {:margin-left   :-20px :margin-right :-20px
                        :margin-bottom :-30px}}
          [chart-1 {:title "健康" :value non-blue-percent}]]
         [:div {:style {:margin-left   :-20px :margin-right :-20px
                        :margin-bottom :-30px}}
          [chart-1 {:title "锻炼" :value (/ active goal-active)
                    :start "#EE0000" :stop "#EE9572"}]]
         [:div {:style {:margin-left   :-20px :margin-right :-20px
                        :margin-bottom :-30px}}
          [chart-1 {:title "习惯" :value (/ clean-count 4)
                    :start "#D8BFD8" :stop "#DDA0DD"}]]
         [:div {:style {:margin-left   :-20px :margin-right :-10px
                        :margin-bottom :-30px}}
          [chart-1 {:title "待办" :value finish-percent
                    :start "#4F94CD" :stop "#87CEEB"}]]]]
       [:div.mt-2.mx-2.box
        [:p.is-size-5.mb-3.has-text-weight-light "快递更新"]
        (if (empty? express)
            [:p "暂无正在追踪的快递。"]
            [:<>
             (for [{:keys [id name status last_update info] :as exp} express]
                  ^{:key exp}
                  [:p {:style {:line-height :1.7em}}
                   (if (= status 1)
                       [:span.tag.is-small.is-rounded.is-light.is-primary.is-size-7.mr-2 "正在追踪"]
                       [:span.tag.is-small.is-light.is-rounded.is-size-7.mr-2 "已经结束"])
                   (or name id)
                   [:span.has-text-grey-light.is-size-7.ml-3 (subs (or info "暂无数据。") 0
                                                                   (if (> (count info) max-word)
                                                                       max-word
                                                                       (count info)))]])])]
       [:div.mt-2.mx-2.box
        [:p.is-size-5.mb-3.has-text-weight-light "影视更新"]
        (if (empty? movie)
            [:p "暂无最近更新的影视剧。"]
            [:div.tags.mb-1
             (for [{:keys [name url data last_update] :as mov} movie]
                  ^{:key mov}
                  [:<>
                   (let [data (sort (or data []))]
                        [:span.tag.is-light.is-info {:style {:line-height :35px}}
                         [:a {:href url :target :_black} name]
                         [:span.has-text-grey-light.is-size-7.ml-3 (or (last data) "暂无数据")]])])])]]
      [:div.column.is-one-third-desktop.pl-0
       (if (= (count todo) 0)
         [:div.mx-2.mt-3.is-unselectable.box
          [:p "没有 Microsoft TODO 数据"]]
         [:div.mx-2.mt-3.is-unselectable.box
          (for [day days]
            ^{:key day}
            [:div.mb-4
             [:span.has-text-weight-bold.is-family-code
              (cond (= day (keyword today)) "今天"
                    (= day (keyword yesterday)) "昨天"
                    :else day)]
             (let [data (get todo day)
                   data (filter #(not (or (str/includes? (:list %) "INSPUR")
                                          (str/includes? (:list %) "任务"))) data)]
               (for [{:keys [time finish_at modified_at create_at
                             title status list importance] :as todo} data]
                 ^{:key todo}
                 [:p.mt-1
                  [:span.tag.is-small.is-rounded.is-size-7.mr-2 list]
                  [:span.is-size-7 title]
                  [:span.is-size-7.has-text-weight-light.has-text-danger
                   (if (not= status "completed") " ×")]]))])])]]]))