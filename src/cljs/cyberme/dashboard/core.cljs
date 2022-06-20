(ns cyberme.dashboard.core
  (:require [re-frame.core :as rf]
            [clojure.string :as str]
            [cljs-time.format :as format]
            [goog.string :as gstring]
            [cljs-time.core :as t]
            [cyberme.util.tool :as tool]
            [cyberme.validation :as va]
            [cyberme.util.form :refer [dialog]]
            [cyberme.util.echarts :refer [ECharts EChartsR EChartsM]]
            [cyberme.util.request :refer [ajax-flow] :as req]
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

(def max-word 43)

(def default-score 0.1)

(def max-score-empty 0.9)

(defn simple-print [data]
  (-> (with-out-str (pprint data))
      (str/replace "," "<br>")
      (str/replace "{" " ")
      (str/replace "}" " ")))

(defn progress-bar
  "根据 API 信息返回计算好的每周进度条指示，数据如下：
  :score {:2022-03-01
          {:blue true
           :fitness {:rest 2000 :active 300}
           :todo {:total 27 :finished 27}}
           :clean {:m1xx :m2xx :n1xx :n2xx}}
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
        ;计算规则：blue 计 2 分，active 完成计 2 分，to-do 完成计 2 分，clean 完成计 2 分，score 计 2 分
        compute-oneday (fn [day-str]
                         (let [{:keys [blue fitness todo clean today]} (get score day-str)
                               is-blue? (boolean blue)
                               ;运动能量大于目标值 && 消耗能量大于目标值
                               finish-active!-1 (>= (or (:active fitness) 0) goal-active)
                               finish-active!-2 (>= (- (+ (:rest fitness) (:active fitness))
                                                       (:diet fitness)) goal-cut)
                               todo-all-done! (and (>= (:finished todo) (:total todo))
                                                   (not= (:total todo) 0))
                               clean-count (count (filter true? (vals clean)))
                               score (+ (if is-blue? 0 2)
                                        (* (or today 0) 0.02)
                                        (if (and finish-active!-1 finish-active!-2) 2 0)
                                        (if todo-all-done! 2 0)
                                        (* clean-count 0.5))]
                           score))
        ;每周进度统计，包含 clean，blue，energy 和 today-score，to-do
        compute-one-day-item (fn [day-str]
                               (let [{:keys [blue fitness todo clean today]} (get score day-str)
                                     ;运动能量大于目标值 && 消耗能量大于目标值
                                     finish-active!-1 (>= (or (:active fitness) 0) goal-active)
                                     finish-active!-2 (>= (- (+ (:rest fitness) (:active fitness))
                                                             (:diet fitness)) goal-cut)
                                     todo-all-done! (and (>= (:finished todo) (:total todo))
                                                         (not= (:total todo) 0))]
                                 {:clean  (every? true? (vals clean))
                                  :blue   (boolean blue)
                                  :energy (and finish-active!-1 finish-active!-2)
                                  :today  (not= today 0)
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

(defn movie-add-dialog
  []
  (dialog :add-movie!
          "添加追踪美剧"
          [[:name "名称 *" "美剧名"]
           [:url "追踪地址 *" "http 或 https 开头"]]
          "确定"
          (fn [f e] (if (not (or (str/blank? (:name @f)) (str/blank? (:url @f))))
                      (rf/dispatch [:movie/movie-add @f])
                      (do
                        (when (str/blank? (:name @f)) (swap! e assoc :name "名称不能为空"))
                        (when (str/blank? (:url @f)) (swap! e assoc :url "地址不能为空")))))
          {:subscribe-ajax    [:movie/movie-data]
           :call-when-exit    [[:movie/movie-data-clean]]
           :call-when-success [[:movie/movie-data-clean]]}))

(ajax-flow {:call    :movie/movie-add
            :uri-fn  #(str "/cyber/movie/?name=" (:name %) "&url=" (:url %))
            :is-post true
            :data    :movie/movie-data
            :clean   :movie/movie-data-clean})

(defn express-add-dialog
  []
  (dialog :add-express!
          "添加追踪快递"
          [[:no "编号 *" "顺丰快递需要在末尾输入 :xxxx 收货人手机号后四位"]
           [:note "备注 *" "此快递的别名"]]
          "确定"
          #(if-let [err (va/validate! @%1 [[:no va/required] [:note va/required]])]
             (reset! %2 err)
             (rf/dispatch [:express/express-add @%1]))
          {:subscribe-ajax    [:express/express-data]
           :call-when-exit    [[:express/express-data-clean]]
           :call-when-success [[:express/express-data-clean]
                               [:dashboard/recent]]}))

(ajax-flow {:call   :express/express-add
            :uri-fn #(str "/cyber/express/track?no=" (:no %) "&note=" (:note %))
            :data   :express/express-data
            :clean  :express/express-data-clean})

(ajax-flow {:call           :hcm/sync
            :uri-fn         #(str "/cyber/check/now?useCache=false&plainText=false")
            :data           :hcm/sync-data
            :clean          :hcm/sync-data-clean
            :success-notice true
            :failure-notice true})

(ajax-flow {:call           :note/last
            :uri-fn         #(str "/cyber/note/last")
            :data           :note/last-data
            :clean          :note/last-data-clean
            :success-notice true
            :failure-notice true
            :notice-with-pre true})

(defn dashboard-page []
  (let [now (t/time-now)
        today (format/unparse-local (format/formatter "yyyy-MM-dd") now)
        week-day (condp = (t/day-of-week now)
                   1 "周一" 2 "周二" 3 "周三" 4 "周四" 5 "周五" 6 "周六" 7 "周日")
        yesterday (format/unparse-local
                    (format/formatter "yyyy-MM-dd")
                    (t/minus (t/time-now) (t/period :days 1)))
        tomorrow (format/unparse-local
                   (format/formatter "yyyy-MM-dd")
                   (t/plus (t/time-now) (t/period :days 1)))
        tomorrow+1 (format/unparse-local
                     (format/formatter "yyyy-MM-dd")
                     (t/plus (t/time-now) (t/period :days 2)))
        month-days (t/number-of-days-in-the-month (t/time-now))

        recent @(rf/subscribe [:dashboard/recent-data])
        day-work-res @(rf/subscribe [:dashboard/day-work-data])
        day-work (-> day-work-res :data)
        {:keys [todo fitness blue clean express movie score work]} (:data recent)
        ;;TODAY-SCORE
        today-score-origin (* (or (:today (:data recent)) 0) 0.01)
        ;不论怎样，都将分数至少设置为 0.1，哪怕还没有日记
        today-score (if (= today-score-origin 0.0) default-score today-score-origin)
        ;;FITNESS
        {:keys [active rest diet goal-active goal-cut]} fitness
        now-cost-energy (- (+ active rest) diet)
        ;;CLEAN
        {:keys [MorningBrushTeeth MorningCleanFace
                NightCleanFace NightBrushTeeth HabitCountUntilNow]} clean
        clean-marvel-count (or (:MarvelCount clean) HabitCountUntilNow)
        clean-count (+ (if MorningBrushTeeth 1 0)
                       (if MorningCleanFace 1 0)
                       (if NightCleanFace 1 0)
                       (if NightBrushTeeth 1 0))
        ;;EXPRESS
        express (filterv #(not= (:status %) 0) express)
        ;;BLUE
        {:keys [MonthBlueCount MaxNoBlueDay]} blue
        blue-marvel-count (or (:MarvelCount blue) MaxNoBlueDay)
        non-blue-percent (- 1 (/ MonthBlueCount month-days))
        ;;MOVIE
        movie (reverse (sort :last_update movie))
        ;;TO-DO
        today-todo (get todo (keyword today) [])
        not-finished-todo (count (filter #(not= (:status %) "completed") today-todo))
        all-todo (count today-todo)
        finished-todo (- all-todo not-finished-todo)
        ;完全无待办 0.0，有待办但是进度为 0，最少 0.1
        finish-percent (cond (= all-todo 0) 0.0
                             (= finished-todo 0) default-score
                             :else (/ finished-todo all-todo))
        days (reverse (sort (keys todo)))
        ;;WORK
        {:keys [NeedWork OffWork NeedMorningCheck WorkHour SignIn Policy]} work
        ;SignIn ;source, time
        {:keys [exist pending success failed policy-count]} Policy
        policy-str (if exist (gstring/format "[%s/%s/%s]"
                                             success
                                             (+ pending success failed)
                                             policy-count)
                             "[0/0/0]")
        ;;SCORE
        ;首先生成今天日期占据本周日期的百分比，以供进度条使用
        {:keys [hint show-pass-percent show-score-percent week-items]}
        (progress-bar score :goal-active goal-active :goal-cut goal-cut)
        ;;PLANT
        plant-info @(rf/subscribe [:dashboard/plant-week-data])
        plant-status (:status (:data plant-info))
        learn-done (= "done" (:learn (:data plant-info)))]
    [:div.container
     [:div.columns
      [:div.column.pr-0
       [:div#circle-info.mx-2.mt-3.box {:style {:margin-bottom :1em}}
        [:p
         [:span.is-size-5.is-family-code.has-text-weight-bold.is-unselectable.mr-3
          [:span.mr-1 "> " today]
          [:span.is-size-6.has-text-weight-normal
           {:style {:vertical-align :1% :font-size :13px}}
           week-day]]
         [:span.is-clickable {:style    {:vertical-align :10%
                                         :font-size      :13px
                                         :color          :lightgray}
                              :on-click #(do (rf/dispatch [:dashboard/recent])
                                             (rf/dispatch [:dashboard/day-work]))}
          [:i.fa.fa-refresh]]]
        [:div.is-flex.is-justify-content-space-around.is-flex-wrap-wrap
         [:div {:style {:margin "-10px -30px -40px -30px"}}
          [chart-1 {:title "待办" :value finish-percent
                    :start "#4F94CD" :stop "#87CEEB"
                    :hint  (simple-print {:total    (count today-todo)
                                          :finished (- (count today-todo)
                                                       not-finished-todo)})}]]
         [:div {:style {:margin "-10px -30px -40px -30px"}}
          [chart-1 {:title "习惯" :value (/ clean-count 4)
                    :start "#D8BFD8" :stop "#DDA0DD"
                    :hint  (simple-print clean)}]]
         [:div {:style {:margin "-10px -30px -40px -30px"}}
          [chart-1 {:title "能量" :value (min (/ now-cost-energy goal-cut) (/ active goal-active))
                    :start "#EE0000" :stop "#EE9572"
                    :hint  (simple-print fitness)}]]
         [:div {:style {:margin "-10px -30px -40px -30px"}}
          [chart-1 {:title "日记" :value today-score
                    :hint  (simple-print {:score (* today-score-origin 100)})}]]]
        [:div.is-flex.is-justify-content-space-around.is-flex-wrap-wrap.tablet-ml-3
         {:style {:margin-top :20px :margin-bottom :3px}}
         [:div.is-align-self-center.px-3 {:style {:margin-left :-10px :margin-right :-20px}}
          [:p.mb-1.mt-1
           (when NeedMorningCheck "⏰ ")
           "已工作 "
           [(if OffWork
              :span.tag.is-rounded.is-small.is-light.is-success
              :span.tag.is-rounded.is-small.is-light.is-warning)
            {:style {:vertical-align :10%}} WorkHour]
           " 小时" (when-not NeedWork "*")
           " " [:span.is-family-code.is-size-7.is-clickable.dui-tips
                {:on-click     #(rf/dispatch [:hcm/sync])
                 :data-tooltip "同步 HCM"} policy-str] " "]
          [:div.tags
           (for [index (range (count SignIn))]
             ^{:key index}
             [:<>
              (let [{:keys [source time]} (get SignIn index)]
                [:span.tag "#" " " (tool/datetime->time time)])])]]
         [:div.is-align-self-center.is-hidden-touch1.px-3
          [:p.mt-2 "习惯已坚持 "
           [:span.is-size-4.is-family-code {:style {:vertical-align "-4%"}} HabitCountUntilNow] " 天"]
          [:p.is-size-7.mb-3.has-text-weight-light "最长坚持 " clean-marvel-count " 天"]]
         [:div.is-align-self-center.is-hidden-touch1.px-3 {:style {:margin-left :-10px}}
          [:p.mt-2 "静心已坚持 "
           [:span.is-size-4.is-family-code {:style {:vertical-align "-4%"}} MaxNoBlueDay] " 天"]
          [:p.is-size-7.mb-3.has-text-weight-light "最长坚持 " blue-marvel-count " 天"]]]]
       [:div#week-info.mx-2.box {:style {:margin-bottom :1em
                                         :position      :relative
                                         :border-radius "6px 6px 0 0"
                                         ;:background-color "#0f224c"
                                         ;:color :white
                                         ;:box-shadow :none
                                         ;:z-index       10
                                         }}
        [:div.columns
         (for [day [0 1 2 3 4 5 6]]
           ^{:key day}
           [:div.column
            [:p.mb-1 (condp = day 0 "周一" 1 "周二" 2 "周三" 3 "周四" 4 "周五" 5 "周六" 6 "周日")]
            (let [{:keys [clean blue energy today todo]} (get week-items day)
                  check-fnn (fn [check good bad]
                              (cond (nil? check) ""
                                    check (if (str/blank? good) "" [:p.mr-1 good])
                                    :else (if (str/blank? bad) "" [:p.mr-1 bad])))]
              [:div.is-size-6.is-family-code {:style {:white-space :nowrap}}
               [:div.is-flex.is-flex-wrap-wrap
                (check-fnn todo "✅" "❌")
                (check-fnn clean "🧼" "")
                (check-fnn energy "🥦" "🧀")
                (check-fnn blue "🕳" "🎭")
                (check-fnn today "🔰️" "")]])])]]
       [:div#progress-info.mx-2.box.px-0.wave.is-flex {:style {:margin-bottom :1em
                                                               :padding-top   :0px
                                                               :overflow      :hidden
                                                               :height        :100px
                                                               ;:background-color "#0F224C"
                                                               :position      :relative
                                                               :border-radius "0 0 6px 6px"
                                                               :top           :-40px
                                                               :box-shadow    "0 .5em 1em -.125em rgba(10,10,10,.1),0 0 0 1px rgba(10,10,10,0)"
                                                               ;:z-index       11
                                                               }}
        [chart-2 {:width show-pass-percent :hint hint :finish show-score-percent}]
        #_[:div.is-family-code.has-text-white
           {:style {:font-size     :70px
                    :line-height   :80px
                    :margin-bottom :-20px}} "27"]]
       [:div#express-info.mx-2.box {:style {:margin-bottom :1em
                                            :margin-top    :-40px}}
        [express-add-dialog]
        [:p.is-size-5.mb-3.has-text-weight-light [:span.mr-1 "快递更新"]
         [:span.is-size-7.is-clickable.dui-tips
          {:on-click     #(rf/dispatch [:app/show-modal :add-express!])
           :data-tooltip "新建快递追踪"} " +"]]
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
       [:div#tv-info.mx-2.box {:style {:margin-bottom :1em}}
        [movie-add-dialog]
        [:p.is-size-5.mb-3.has-text-weight-light [:span.mr-1 "影视更新"]
         [:span.is-size-7.is-clickable.dui-tips
          {:on-click     #(rf/dispatch [:app/show-modal :add-movie!])
           :data-tooltip "新建影视追踪"} " +"]]
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
         [:div#todo-info.mx-2.mt-3.is-unselectable.box
          [:p "没有 Microsoft TODO 数据"]]
         [:div#todo-info.mx-2.mt-3.is-unselectable.box
          (for [day days]
            ^{:key day}
            [:<>
             (if (= day (keyword today))
               [:div.mb-5 {:style {:background-color "#f5f5f5"
                                   :outline          "13px solid #f5f5f5"
                                   :border-radius    :0px}}
                (let [data (get todo day)
                      data (filter #(and (not (or #_(str/includes? (:list %) "INSPUR")
                                                (str/includes? (:list %) "任务")))
                                         (= (:importance %) "high")) data)
                      finished-count (count (filter #(= (:status %) "completed") data))
                      all-count (count data)
                      data (sort (fn [{s1 :status c1 :create_at :as a1} {s2 :status c2 :create_at :as a2}]
                                   (cond (= s1 s2) (compare c2 c1)
                                         (= "completed" s1) 100
                                         (= "completed" s2) -100
                                         :else (compare a1 a2))) data)]
                  [:<>
                   [:span.has-text-weight-bold.is-family-code "我的一天"
                    [:span.has-text-weight-normal
                     (gstring/format " (%s/%s)" finished-count all-count)]
                    [:span.has-text-weight-normal.is-size-6.is-clickable
                     {:on-click #(rf/dispatch [:dashboard/plant-week-set-today])}
                     " "
                     [:<>
                      (for [plant plant-status]
                        ^{:key (random-uuid)}
                        [:<>
                         (if (= plant 1) [:i.fa.fa-pagelines.has-text-success]
                                         [:i.fa.fa-pagelines {:style {:color "#ddd"}}])])]
                     " "]
                    (if day-work
                      [:span.has-text-weight-normal.is-size-7.has-text-info.is-clickable
                       {:on-click #(do (rf/dispatch [:dashboard/day-work-edit nil])
                                       (rf/dispatch [:dashboard/day-work]))}
                       day-work]
                      [:span.has-text-weight-normal.is-size-7.has-text-danger.is-clickable
                       {:on-click #(do (rf/dispatch [:global/notice {:message "已经完成日报吗？"
                                                                     :callback [:dashboard/day-work-edit "已完成日报"]}])
                                       (.open js/window "http://10.110.88.102/pro/effort-calendar.html#app=my" "_blank"))}
                       "没有日报"])
                    [:span " "]
                    (if learn-done
                      [:span.has-text-weight-normal.is-size-7.has-text-info.is-clickable.dui-tips
                       {:on-click #(do (rf/dispatch [:dashboard/learn-week-set-today {:non-end true}]))
                        :data-tooltip "已完成本周一学"}
                       "TRAIN-WEEK"]
                      [:span.has-text-weight-normal.is-size-7.has-text-danger.is-clickable.dui-tips
                       {:on-click #(do (rf/dispatch [:global/notice {:message "已经完成每周一学任务了吗？"
                                                                     :callback [:dashboard/learn-week-set-today {:end true}]}])
                                       (.open js/window "https://edu.inspur.com" "_blank"))
                        :data-tooltip "未完成本周一学!"}
                       "TRAIN-WEEK!!"])
                    [:span " "]
                    [:span.has-text-weight-normal.is-size-7.has-text-info.is-clickable.dui-tips
                     {:on-click #(do (rf/dispatch [:dashboard/learn-week-set-today {:start true}]))
                      :data-tooltip "新建学习请求"}
                     "+"]]
                   (for [{:keys [title status list] :as todo} data]
                     ^{:key todo}
                     [:p.mt-1
                      [:span.tag.is-small.is-rounded.is-size-7.mr-2.is-white list]
                      [:span.is-size-7 (when (= status "completed")
                                         {:style {:text-decoration :line-through}})
                       title]
                      #_[:span.is-size-7.has-text-weight-light.has-text-danger
                         (if (not= status "completed") " ×")]])])]
               [:div.mb-4 {:style {:opacity 0.5}}
                [:span.has-text-weight-bold.is-family-code
                 (cond (= day (keyword today)) "今天"
                       (= day (keyword yesterday)) "昨天"
                       (= day (keyword tomorrow)) "明天"
                       (= day (keyword tomorrow+1)) "后天"
                       :else day)]
                (let [data (get todo day)
                      data (filter #(not (or #_(str/includes? (:list %) "INSPUR")
                                           (str/includes? (:list %) "任务"))) data)
                      #_data #_(sort (fn [{s1 :status c1 :create_at l1 :list} {s2 :status c2 :create_at l2 :list}]
                                   (cond (= l1 l2)
                                         (cond (and (= "completed" s1) (= s1 s2)) (compare c2 c1)
                                               (= "completed" s1) 100
                                               (= "completed" s2) -100
                                               :else (compare c2 c1))
                                         :else (compare l1 l2))) data)]
                  (for [{:keys [time finish_at modified_at create_at
                                title status list importance] :as todo} data]
                    ^{:key todo}
                    [:p.mt-1
                     [:span.tag.is-small.is-rounded.is-size-7.mr-2 list]
                     [:span.is-size-7 title]
                     [:span.is-size-7.has-text-weight-light.has-text-danger
                      (if (not= status "completed") " ×")]]))])])])]]]))
