(ns cyberme.dashboard.core
  (:require [re-frame.core :as rf]
            [clojure.string :as str]
            [cljs-time.format :as format]
            [goog.string :as gstring]
            [cljs-time.core :as t]
            [cyberme.util.echarts :refer [ECharts EChartsR EChartsM]]
            [cljs.pprint :refer [pprint]]))

(defn chart-1 [{:keys [title value width height start stop hint]
                :or   {width "200px" height "200px"
                       title "未命名" value 0.8
                       start "#2fdb9a" stop "#1cbab4"}}]
  [EChartsM
   {:style {:width width :height height}
    :option
    {:title   {:text      title
               :x         "center"
               :y         "38.5%"
               :zlevel    3
               :textStyle {:fontWeight "700"
                           :color      "#303030"
                           :fontSize   "20"}}
     :tooltip {:formatter (or hint title) #_(clj->js (fn [params ticket callback]
                                                       (let [{{:keys [name value]} :data}
                                                             (js->clj params :keywordize-keys true)])))
               :position  ["70%" "60%"]}
     :legend  {:show false}
     :series  [{:type      "pie"
                :zlevel    2
                :radius    ["0" "10%"]
                :center    ["50%" "45%"]
                :label     {:show false}
                :clockwise false
                :animation false
                :itemStyle {:shadowBlur  0
                            :shadowColor ""
                            :color       "#fff"
                            :label       {:show false}
                            :labelLine   {:show false}
                            :borderCap   "round"
                            :borderJoin  "round"}
                ;:hoverAnimation false
                :data      [100]}
               {:type      "pie"
                :label     {:show false}
                :clockwise false
                :radius    ["40%" "62%"]
                :center    ["50%" "45%"]
                :itemStyle {:label     {:show false}
                            :labelLine {:show false}}
                ;:hoverAnimation false
                :data      [{:value     (* value 100)
                             :name      title
                             :itemStyle {:color
                                         {:type "linear"
                                          :x    0 :y 0 :x2 1 :y2 1
                                          :colorStops
                                          [{:offset 0 :color start}
                                           {:offset 1 :color stop}]}
                                         :borderRadius ["10%" "10%"]}}
                            {:value     (- 100 (* value 100))
                             :name      "Other"
                             :itemStyle {:color
                                         {:colorStops
                                          [{:offset 0 :color "#F7F7F7"}
                                           {:offset 1 :color "#F7F7F7"}]}
                                         :borderRadius ["10%" "10%"]}}]}
               {:type      "pie"
                :clockwise false
                :label     {:show false}
                :radius    ["62%" "70%"]
                :center    ["50%" "45%"]
                :itemStyle {:borderCap   "round"
                            :borderJoin  "round"
                            :shadowBlur  0
                            :shadowColor "rgba(0,0,0,.2)"
                            :color       "#fff"
                            :label       {:show false}
                            :labelLine   {:show false}}
                ;:hoverAnimation false
                :data      [100]}]}
    }])

(defn chart-2 [{:keys [title value width height hint finish]
                :or   {height "100px" width "50%" hint "Nothing HERE" finish 0.5
                       title "未命名" value 0.8}}]
  [EChartsM
   {:style {:width width :height height}
    :option
    {:backgroundColor "#0F224C"
     :series
     [{:type            "liquidFill"
       :radius          "60%"
       :silent          true
       :center          ["50%" "50%"]
       :amplitude       10
       :data            [finish finish finish]
       :itemStyle       {:opacity :0.4}
       :shape           "container"
       :color           [{:type        "linear"
                          :x           0 :y 0 :x2 0 :y2 1
                          :colorStops  [{:offset 0 :color "#446bf5"}
                                        {:offset 1 :color "#2ca3e2"}]
                          :globalCoord false}]
       :backgroundStyle {:borderWidth 10
                         :color       "#0F224C"
                         :borderColor "transparent"
                         :shadowColor "red"
                         :shadowBlur  100}
       :label           {:position  ["50%" "55%"]
                         :formatter hint
                         :textStyle {:fontSize "24px"
                                     :color    "#fff"}}
       :outline         {:show false}}]}}])

(def goal-active 500)

(def max-word 43)

(defn simple-print [data]
  (-> (with-out-str (pprint data))
      (str/replace "," "<br>")
      (str/replace "{" " ")
      (str/replace "}" " ")))

(defn progress-bar
  "根据 API 信息返回计算好的每周进度条指示
  :score {:2022-03-01
          {:blue true
           :fitness {:rest 2000 :active 300}
           :todo {:total 27 :finished 27}}
           :clean {:m1xx :m2xx :n1xx :n2xx}}}"
  [score]
  (let [now (t/time-now)
        week-index (t/day-of-week now)
        week-start (t/minus now (t/days (- week-index 1)))
        ;;只统计到昨天，今天的不算
        week-list (take (- week-index 1) (iterate #(t/plus % (t/days 1)) week-start))
        week-list (mapv (comp keyword #(format/unparse-local
                                         (format/formatter "yyyy-MM-dd") %))
                        week-list)
        score-pass-all (* (- week-index 1) 8)
        score-have (reduce (fn [acc today-kw]
                             (+ acc
                                (let [{:keys [blue fitness todo clean]} (get score today-kw)
                                      is-blue? (boolean blue)
                                      finish-active! (>= (or (:active fitness) 0) goal-active)
                                      todo-all-done! (>= (or (:finished todo) 0) (or (:total todo) 0))
                                      clean-count (count (filter true? (vals clean)))]
                                  (+ (if is-blue? 0 2)
                                     (if finish-active! 2 0)
                                     (if todo-all-done! 2 0)
                                     (* clean-count 0.5)))))
                           0 week-list)
        finish-percent (if (= score-pass-all 0) 0 (/ score-have score-pass-all))]
    {:pass-percent (gstring/format "%.0d%%" (* (/ (- week-index 1) 7.0) 100))
     :score score-have
     :hint (if (= week-index 1)
             "新的一周开始了"
             (gstring/format "本周已达成 %.0f%% 目标" (* finish-percent 100)))
     :score-percent finish-percent}))

(defn dashboard-page []
  (let [today (format/unparse-local
                (format/formatter "yyyy-MM-dd")
                (t/time-now))
        yesterday (format/unparse-local
                    (format/formatter "yyyy-MM-dd")
                    (t/plus (t/time-now) (t/period :days 1)))
        month-days (t/number-of-days-in-the-month (t/time-now))

        recent @(rf/subscribe [:dashboard/recent-data])
        {:keys [todo fitness blue clean express movie score]} (:data recent)
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
        days (reverse (sort (keys todo)))
        ;;SCORE
        ;首先生成今天日期占据本周日期的百分比，以供进度条使用
        {:keys [pass-percent
                hint
                score-percent]} (progress-bar score)]
    [:div.container
     [:div.columns
      [:div.column.pr-0
       [:div.mx-2.mt-3.box {:style {:margin-bottom :1em}}
        [:p [:span.is-size-5.is-family-code.has-text-weight-bold.is-unselectable
             "> " today]]
        [:div.is-flex {:style {:flex-wrap :wrap}}
         [:div {:style {:margin-left   :-20px :margin-right :-20px
                        :margin-bottom :-30px}}
          [chart-1 {:title "健康" :value non-blue-percent
                    :hint  (simple-print blue)}]]
         [:div {:style {:margin-left   :-20px :margin-right :-20px
                        :margin-bottom :-30px}}
          [chart-1 {:title "锻炼" :value (/ active goal-active)
                    :start "#EE0000" :stop "#EE9572"
                    :hint  (simple-print fitness)}]]
         [:div {:style {:margin-left   :-20px :margin-right :-20px
                        :margin-bottom :-30px}}
          [chart-1 {:title "习惯" :value (/ clean-count 4)
                    :start "#D8BFD8" :stop "#DDA0DD"
                    :hint  (simple-print clean)}]]
         [:div {:style {:margin-left   :-20px :margin-right :-10px
                        :margin-bottom :-30px}}
          [chart-1 {:title "待办" :value finish-percent
                    :start "#4F94CD" :stop "#87CEEB"
                    :hint  (simple-print {:total    (count today-todo)
                                          :finished (- (count today-todo)
                                                       not-finished)})}]]]]
       [:div.mx-2.box.px-0.wave.is-flex {:style {:margin-bottom    :1em
                                                 :padding-top      :0px
                                                 :overflow         :hidden
                                                 :height           :100px
                                                 :background-color "#0F224C"}}
        [chart-2 {:width pass-percent :hint hint :finish score-percent}]
        #_[:div.is-family-code.has-text-white
           {:style {:font-size     :70px
                    :line-height   :80px
                    :margin-bottom :-20px}} "27"]]
       [:div.mx-2.box {:style {:margin-bottom :1em}}
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
       [:div.mx-2.box {:style {:margin-bottom :1em}}
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
                   data (filter #(not (or #_(str/includes? (:list %) "INSPUR")
                                        (str/includes? (:list %) "任务"))) data)]
               (for [{:keys [time finish_at modified_at create_at
                             title status list importance] :as todo} data]
                 ^{:key todo}
                 [:p.mt-1
                  [:span.tag.is-small.is-rounded.is-size-7.mr-2 list]
                  [:span.is-size-7 title]
                  [:span.is-size-7.has-text-weight-light.has-text-danger
                   (if (not= status "completed") " ×")]]))])])]]]))
