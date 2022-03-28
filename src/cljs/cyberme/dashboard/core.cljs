(ns cyberme.dashboard.core
  (:require [re-frame.core :as rf]
            [clojure.string :as str]
            [cljs-time.format :as format]
            [goog.string :as gstring]
            [cljs-time.core :as t]
            [cyberme.util.echarts :refer [ECharts EChartsR EChartsM]]
            [cljs.pprint :refer [pprint]]))

(defn chart-1
  "圆环图表，基于 ECharts Pie 图"
  [{:keys [title value width height start stop hint]
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

(defn chart-2
  "进度条图表，基于 ECharts liquidFill"
  [{:keys [width height hint finish]
    :or   {height "100px" width "100%" hint "正在加载数据..." finish 0.1}}]
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
       :label           {:position  ["50%" "53%"]
                         :formatter hint
                         :textStyle {:fontSize "24px"
                                     :color    "#fff"}}
       :outline         {:show false}}]}}])

(def max-word 43)

(defn simple-print [data]
  (-> (with-out-str (pprint data))
      (str/replace "," "<br>")
      (str/replace "{" " ")
      (str/replace "}" " ")))

;;TODO 加一个更新按钮，获取 API 时进度条显示正在加载而非 50%，今日完毕则直接入条

(defn progress-bar
  "根据 API 信息返回计算好的每周进度条指示，数据如下：
  :score {:2022-03-01
          {:blue true
           :fitness {:rest 2000 :active 300}
           :todo {:total 27 :finished 27}}
           :clean {:m1xx :m2xx :n1xx :n2xx}}}
  返回的规则如下：
  pass-percent 返回本周过了多久，粒度为天，返回百分比字符串，对于周一返回 100%
  hint 返回字符串提示，周一返回一句话，其余时间返回过去每天平均达成百分比，
       返回的数据必须能够让前端在不同尺寸设备上都完整显示文字。
  score-percent 返回此百分比的数值
  show-pass-percent 为了激发完成潜力，每天如果所有圆环满则将今天的数据也纳入计算
  show-score-percent 同上，今天圆环满则将今天数据也纳入计算"
  [score & {:keys [goal-active]}]
  (let [now (t/time-now)
        format-date #(format/unparse-local (format/formatter "yyyy-MM-dd") %)
        week-1-hint (str "Week #" (t/week-number-of-year now))
        week-n-hint (condp = (t/day-of-week now)
                      1 "%.0f%%"
                      2 "%.0f%%"
                      7 "本周平均达成 %.0f%%"
                      "平均达成 %.0f%%")
        week-index (t/day-of-week now)
        week-start (t/minus now (t/days (- week-index 1)))
        ;分别计算包含/不包含今天的数据
        week-list-f (take (- week-index 1) (iterate #(t/plus % (t/days 1)) week-start))
        week-list (mapv (comp keyword format-date) week-list-f)
        each-day-score 8
        score-pass-all-f (* week-index each-day-score)
        score-pass-all (* (- week-index 1) each-day-score)
        ;计算规则：blue 计 2 分，active 完成计 2 分，to-do 完成计 2 分，clean 完成计 2 分
        ;TODO 等待日志系统完善后使用自评而非 blue 评分，但是接口也需要提供自评而非 blue 得分
        compute-oneday (fn [day-str]
                         (let [{:keys [blue fitness todo clean]} (get score day-str)
                               is-blue? (boolean blue)
                               finish-active! (>= (or (:active fitness) 0) goal-active)
                               todo-all-done! (>= (or (:finished todo) 0) (or (:total todo) 0))
                               clean-count (count (filter true? (vals clean)))]
                           (+ (if is-blue? 0 2)
                              (if finish-active! 2 0)
                              (if todo-all-done! 2 0)
                              (* clean-count 0.5))))
        score-have (fn [week-list] (reduce #(+ %1 (compute-oneday %2)) 0 week-list))
        finish-percent-f (/ (score-have week-list-f) score-pass-all-f)
        finish-percent (if (= score-pass-all 0)
                         0 (/ (score-have week-list) score-pass-all))]
    (let [today-all-finished? (>= (compute-oneday (format-date now)) each-day-score)
          pass-percent (if (= week-index 1)
                         "100%" (gstring/format "%.0d%%" (* (/ (- week-index 1) 7.0) 100)))
          pass-percent-f (gstring/format "%.0d%%" (* (/ week-index 7.0) 100))
          hint (if (= week-index 1)
                 week-1-hint (gstring/format week-n-hint (* finish-percent 100)))
          hint-f (gstring/format week-n-hint (* finish-percent-f 100))]
      {:pass-percent       (if today-all-finished? pass-percent-f pass-percent)
       :score-percent      finish-percent
       :hint               (if today-all-finished? hint-f hint)
       :show-pass-percent  (if today-all-finished? pass-percent-f pass-percent)
       :show-score-percent (if today-all-finished? finish-percent-f finish-percent)})))

(defn dashboard-page []
  (let [now (t/time-now)
        today (format/unparse-local (format/formatter "yyyy-MM-dd") now)
        week-day (condp = (t/day-of-week now)
                   1 "周一" 2 "周二" 3 "周三" 4 "周四" 5 "周五" 6 "周六" 7 "周日")
        yesterday (format/unparse-local
                    (format/formatter "yyyy-MM-dd")
                    (t/plus (t/time-now) (t/period :days 1)))
        month-days (t/number-of-days-in-the-month (t/time-now))

        recent @(rf/subscribe [:dashboard/recent-data])
        {:keys [todo fitness blue clean express movie score]} (:data recent)
        {:keys [active rest goal-active]} fitness
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
        {:keys [hint show-pass-percent show-score-percent]}
        (progress-bar score :goal-active goal-active)]
    [:div.container
     [:div.columns
      [:div.column.pr-0
       [:div.mx-2.mt-3.box {:style {:margin-bottom :1em}}
        [:p
         [:span.is-size-5.is-family-code.has-text-weight-bold.is-unselectable.mr-3
          [:span.mr-1 "> " today]
          [:span.is-size-6.has-text-weight-normal
           {:style {:vertical-align :1% :font-size :13px}}
           week-day]]
         [:span.is-clickable {:style    {:vertical-align :10%
                                         :font-size      :13px
                                         :color          :lightgray}
                              :on-click #(rf/dispatch [:dashboard/recent])}
          [:i.fa.fa-refresh]]]
        [:div.is-flex {:style {:flex-wrap :wrap}}
         [:div {:style {:margin-left :-20px :margin-right :-20px :margin-bottom :-30px}}
          [chart-1 {:title "锻炼" :value (/ active goal-active)
                    :start "#EE0000" :stop "#EE9572"
                    :hint  (simple-print fitness)}]]
         [:div {:style {:margin-left :-20px :margin-right :-20px :margin-bottom :-30px}}
          [chart-1 {:title "习惯" :value (/ clean-count 4)
                    :start "#D8BFD8" :stop "#DDA0DD"
                    :hint  (simple-print clean)}]]
         [:div {:style {:margin-left :-20px :margin-right :-20px :margin-bottom :-30px}}
          [chart-1 {:title "待办" :value finish-percent
                    :start "#4F94CD" :stop "#87CEEB"
                    :hint  (simple-print {:total    (count today-todo)
                                          :finished (- (count today-todo)
                                                       not-finished)})}]]
         ;TODO 等待完善日志系统并提供自评得分
         [:div {:style {:margin-left :-20px :margin-right :-10px :margin-bottom :-30px}}
          [chart-1 {:title "自省" :value 0.9
                    :hint  (simple-print {:hint "等待施工"})}]]]
        [:div.is-flex.is-justify-content-space-around.is-flex-wrap-wrap.tablet-ml-3
         {:style {:margin-left :-30px :margin-top :20px :margin-bottom :7px}}
         [:div.is-align-self-center {:style {:margin-left :-10px :margin-right :-20px}}
          [:p.mb-2
           "已工作 "
           [:span.tag.is-rounded.is-small.is-light.is-success
            {:style {:vertical-align :10%}} "10.3"]
           " 小时"
           " [2/2] "]
          [:div.tags
           [:span.tag  "上午打卡：08:20"]
           [:span.tag "下午打卡：17:50"]]]
         [:div.is-align-self-center.is-hidden-touch {:style {:margin-left :-10px}}
          [:p.mt-2 "健身已坚持 "
           [:span.is-size-4.is-family-code "100"] " 天"]
          [:p.is-size-7.mb-3.has-text-weight-light "最长坚持 100 天"]]
         [:div.is-align-self-center.is-hidden-touch
          [:p.mt-2 "习惯已坚持 "
           [:span.is-size-4.is-family-code "100"] " 天"]
          [:p.is-size-7.mb-3.has-text-weight-light "最长坚持 100 天"]]]]
       [:div.mx-2.box.px-0.wave.is-flex {:style {:margin-bottom    :1em
                                                 :padding-top      :0px
                                                 :overflow         :hidden
                                                 :height           :100px
                                                 :background-color "#0F224C"}}
        [chart-2 {:width show-pass-percent :hint hint :finish show-score-percent}]
        #_[:div.is-family-code.has-text-white
           {:style {:font-size     :70px
                    :line-height   :80px
                    :margin-bottom :-20px}} "27"]]
       [:div.mx-2.box {:style {:margin-bottom :1em}}
        [:p.is-size-5.mb-3.has-text-weight-light "快递更新"]
        (if (empty? express)
          [:p.is-size-6.has-text-grey "暂无正在追踪的快递。"]
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
          [:p.is-size-6.has-text-grey "暂无最近更新的影视剧。"]
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
            [:<>
             (if (= day (keyword today))
               [:div.mb-5 {:style {:background-color "#f5f5f5"
                                   :outline          "13px solid #f5f5f5"
                                   :border-radius    :0px}}
                (let [data (get todo day)
                      data (filter #(not (or #_(str/includes? (:list %) "INSPUR")
                                           (str/includes? (:list %) "任务"))) data)
                      finished-count (count (filter #(= (:status %) "completed") data))
                      all-count (count data)]
                  [:<>
                   [:span.has-text-weight-bold.is-family-code "我的一天"
                    [:span.has-text-weight-normal
                     (gstring/format "（完成 %s / 合计 %s）" finished-count all-count)]]
                   (for [{:keys [title status list] :as todo} data]
                     ^{:key todo}
                     [:p.mt-1
                      [:span.tag.is-small.is-rounded.is-size-7.mr-2.is-white list]
                      [:span.is-size-7 title]
                      [:span.is-size-7.has-text-weight-light.has-text-danger
                       (if (not= status "completed") " ×")]])])]
               [:div.mb-4 {:style {:opacity 0.5}}
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
                      (if (not= status "completed") " ×")]]))])])])]]]))
