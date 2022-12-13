(ns cyberme.dashboard.chart
  (:require [cljs-time.core :as t]
            [cljs-time.format :as format]
            [clojure.string :as str]
            [cyberme.util.echarts :refer [ECharts EChartsM EChartsR]]
            [goog.string :as gstring]))

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
    {:backgroundColor "transparent" #_"#0F224C"
     :series
     [{:type            "liquidFill"
       :radius          "60%"
       :silent          true
       :center          ["50%" "50%"]
       :amplitude       10
       :data            [finish finish finish]
       :itemStyle       {:opacity    :0.4
                         :shadowBlur :0.0}
       :shape           "container"
       :color           [{:type        "linear"
                          :x           0 :y 0 :x2 0 :y2 1
                          :colorStops  [{:offset 0 :color "#446bf5"}
                                        {:offset 1 :color "#2ca3e2"}]
                          :globalCoord false}]
       :backgroundStyle {:borderWidth 10
                         :color       "transparent" #_"#0F224C"
                         :borderColor "transparent"
                         :shadowColor "red"
                         :shadowBlur  100}
       :label           {:position  ["50%" "53%"]
                         :formatter hint
                         :textStyle {:fontSize "24px"
                                     :color    (if (> finish 0.4)
                                                 "#fff" "#5cafeb")}}
       :outline         {:show false}}]}}])

(defn progress-bar
  "根据 API 信息返回计算好的每周进度条指示，数据如下：
  :score {:2022-03-01
          {:fitness {:rest 2000 :active 300}
           :todo {:total 27 :finished 27}}}
           :today 23}
  返回的规则如下：
  pass-percent 返回本周过了多久，粒度为天，返回百分比字符串，对于周一返回 100%
  hint 返回字符串提示，周一返回一句话，其余时间返回过去每天平均达成百分比，
       返回的数据必须能够让前端在不同尺寸设备上都完整显示文字。
  score-percent 返回此百分比的数值
  show-pass-percent 为了激发完成潜力，每天如果所有圆环满则将今天的数据也纳入计算
  show-score-percent 同上，今天圆环满则将今天数据也纳入计算"
  [score & {:keys [goal-active goal-cut]}]
  (let [now (t/time-now)
        format-date #(format/unparse-local (format/formatter "yyyy-MM-dd") %)
        week-1-hint (str "Week #" (t/week-number-of-year now))
        week-n-hint (condp = (t/day-of-week now)
                      1 "%.0f%%"
                      2 "%.0f%%"
                      3 "%.0f%%"
                      7 "本周平均达成 %.0f%%"
                      "平均达成 %.0f%%")
        week-index (t/day-of-week now)
        week-start (t/minus now (t/days (- week-index 1)))
        ;分别计算包含/不包含今天的数据
        week-gen (iterate #(t/plus % (t/days 1)) week-start)
        week-list-f (mapv (comp keyword format-date) (take week-index week-gen))
        week-list (mapv (comp keyword format-date) (take (- week-index 1) week-gen))
        each-day-score 10
        satisfied-each-day-score 8
        score-pass-all-f (* week-index each-day-score)
        score-pass-all (* (- week-index 1) each-day-score)
        ;计算规则：active 完成计 3 分，to-do 完成计 3 分，mindful 计 2 分
        compute-oneday (fn [day-str]
                         (let [{:keys [blue fitness todo clean today]} (get score day-str)
                               finish-active! (>= (or (:active fitness) 0) goal-active)
                               mind-5! (>= (or (:mindful fitness) 0) 5)
                               todo-all-done! (and (>= (:finished todo) (:total todo))
                                                   (not= (:total todo) 0))
                               clean-count (count (filter true? (vals clean)))
                               score (+ (if mind-5! 2 0)
                                        (if finish-active! 3 0)
                                        (if todo-all-done! 3 0))]
                           score))
        ;每周进度统计
        compute-one-day-item (fn [day-str]
                               (let [{:keys [fitness todo]} (get score day-str)
                                     ;运动能量大于目标值
                                     finish-active! (>= (or (:active fitness) 0) goal-active)
                                     mind-5! (>= (or (:mindful fitness) 0) 5)
                                     todo-all-done! (and (>= (:finished todo) (:total todo))
                                                         (not= (:total todo) 0))]
                                 {:active finish-active!
                                  :mindful mind-5!
                                  :todo   todo-all-done!}))
        week-items (mapv compute-one-day-item week-list)
        week-items-f (mapv compute-one-day-item week-list-f)
        score-have (fn [week-list] (reduce #(+ %1 (compute-oneday %2)) 0 week-list))
        finish-percent-f (/ (score-have week-list-f) score-pass-all-f)
        finish-percent (if (= score-pass-all 0)
                         0 (/ (score-have week-list) score-pass-all))]
    (let [today-all-finished? (>= (compute-oneday (keyword (format-date now)))
                                  satisfied-each-day-score)
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
       :show-score-percent (if today-all-finished? finish-percent-f finish-percent)
       :week-items         (if today-all-finished? week-items-f week-items)})))

#_(defn fit-diary-todo-plan-chart [now show-today data]
  (let [now (or now (t/time-now))
        year (t/year now)
        month (t/month now)
        today (t/day now)
        after-noon? (> (t/hour now) 12)
        last_day (t/last-day-of-the-month now)
        date-format #(gstring/format "%02d-%02d-%02d" year month %)
        calendar-range (gstring/format "%02d-%02d" year month)
        date-key #(keyword (date-format %))
        status-fn #(if-not (->> % date-key (get data) :work-day) "休" "")
        plan-fn #(let [{:keys [exist failed success pending]} (->> % date-key (get data) :policy)]
                   (cond (not exist) ""
                         (and (= pending 0) (not= success 0)) (if (> success 1) "成" "可")
                         (not= pending 0) "候"
                         ;(and (not= failed 0) (= pending 0)) "败"
                         :else "策"))
        day-list (range 1 (+ 1 (t/day last_day)))
        month-list (mapv (fn [d]
                           [(date-format d)
                            (if (and (= d today) show-today) (str "今 " d) d)
                            (status-fn d)
                            (plan-fn d)]) day-list)
        work-list (mapv (fn [d]
                          (let [{hour :work-hour} (get data (date-key d))]
                            [(date-format d)
                             hour])) day-list)]
    [EChartsM
     {:style {:width "500px" :height "340px"}
      :option
      {:tooltip   {:formatter (clj->js (fn [param]
                                         (let [pa (js->clj param)
                                               dd (get pa "data")
                                               {:keys [check-start check-end work-hour]}
                                               (get data (keyword (first dd)))
                                               start-time (second (str/split (:time check-start) "T"))
                                               end-time (second (str/split (:time check-end) "T"))
                                               exist-end? (not= end-time start-time)]
                                           (cond (and check-start check-end exist-end?)
                                                 (str "<b>工作时长：</b>" work-hour " 小时"
                                                      "<br>"
                                                      "<b>首次打卡</b>：" start-time
                                                      "<br>"
                                                      "<b>末次打卡</b>：" end-time)
                                                 check-start
                                                 (str "<b>工作时长：</b>" work-hour " 小时"
                                                      "<br>"
                                                      "<b>首次打卡</b>：" start-time)
                                                 (not= work-hour 0.0)
                                                 (str "<b>工作时长：</b>" work-hour " 小时")
                                                 :else (str "")))))}
       :visualMap {:show        true
                   :min         0
                   :max         13
                   :calculable  true
                   :right       "10%"
                   :top         "1%"
                   :inRange     {:color   ["#dbdbdb" "#dddddd" "#d2d2d2" "#5f5f5f" "#5f5f5f"]
                                 :opacity [0 0.6]}
                   :controller  {:inRange    {:opacity [0 0.6]
                                              :color   ["#dbdbdb" "#dddddd" "#d2d2d2" "#5f5f5f"]}
                                 :outOfRange {:color "#ccc"}}
                   :seriesIndex [0]
                   :orient      "vertical"}
       :calendar  [{:left       "1%"
                    :top        "15%"
                    :cellSize   [50 50]
                    :splitLine  {:show false}
                    :itemStyle  {:color       "#fff"
                                 :borderColor "#ccc"
                                 :borderWidth 0}
                    :yearLabel  {:show false}
                    :orient     "vertical"
                    :dayLabel   {:firstDay 1
                                 :nameMap  "cn"}
                    :monthLabel {:show false}
                    :range      calendar-range}]
       :series    [{:type             "heatmap"
                    :name             "工作时长"
                    :coordinateSystem "calendar"
                    :data             work-list}
                   {:type             "scatter"
                    :coordinateSystem "calendar"
                    :symbolSize       0
                    :label            {:show      true
                                       :formatter (clj->js (fn [p]
                                                             (let [data (get (js->clj p) "data")]
                                                               (str (second data) "\n"))))
                                       :color     "#000"}
                    :data             month-list}
                   {:type             "scatter"
                    :coordinateSystem "calendar"
                    :symbolSize       0
                    :label            {:show       true
                                       :formatter  (clj->js (fn [p]
                                                              (let [data (get (js->clj p) "data")]
                                                                (if-not (str/blank? (nth data 3))
                                                                  (str "\n\n" (nth data 2) "    ")
                                                                  (str "\n\n" (nth data 2) "")))))
                                       :color      "#a00"
                                       :fontSize   11
                                       :fontWeight 700}
                    :data             month-list}
                   {:type             "scatter"
                    :coordinateSystem "calendar"
                    :symbolSize       0
                    :label            {:show       true
                                       :formatter  (clj->js (fn [p]
                                                              (let [data (get (js->clj p) "data")]
                                                                (if-not (str/blank? (nth data 2))
                                                                  (str "\n\n" "     " (nth data 3))
                                                                  (str "\n\n" "" (nth data 3))))))
                                       :color      "rgb(79, 148, 220)"
                                       :fontSize   11
                                       :fontWeight 700
                                       }
                    :data             month-list}]}}]))