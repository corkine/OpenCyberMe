(ns cyberme.dashboard.core
  (:require [cljs-time.core :as t]
            [cljs-time.format :as format]
            [cljs.pprint :refer [pprint]]
            [clojure.string :as str]
            [cyberme.dashboard.chart :refer [chart-1 chart-2 progress-bar]]
            [cyberme.dashboard.week-plan :as week]
            [cyberme.util.form :refer [dialog]]
            [cyberme.util.tool :as tool]
            [cyberme.validation :as va]
            [goog.string :as gstring]
            [re-frame.core :as rf]))

(def max-word 43)

(def default-score 0.1)

(def max-score-empty 0.9)

(defn simple-print [data]
  (-> (with-out-str (pprint data))
      (str/replace "," "<br>")
      (str/replace "{" " ")
      (str/replace "}" " ")))

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
        {:keys [MonthBlueCount MaxNoBlueDay Day120BalanceDay]} blue
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
        learn-done (= "done" (:learn (:data plant-info)))
        ;;WEEK-PLAN
        week-plans @(rf/subscribe [:dashboard/week-plan])]
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
                              :on-click #(rf/dispatch [:dashboard/sync-all])}
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
          [:p.mt-2 "平衡已坚持 "
           [:span.is-size-4.is-family-code {:style {:vertical-align "-4%"}} Day120BalanceDay] " 天"]
          [:p.is-size-7.mb-3.has-text-weight-light "Blue 最长坚持 " blue-marvel-count " 天"]]]]
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
       [:div#progress-info.mx-2.box.px-0.wave.is-flex
        {:style {:margin-bottom :1em
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
           :data-tooltip "新建快递追踪 "} " +"]]
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
      ;DASHBOARD 右侧边栏
      [:div.column.is-one-third-desktop.pl-0
       (if (= (count todo) 0)
         ;最近今天都没有 TO DO 数据，可能是接口异常
         [:div#todo-info.mx-2.mt-3.is-unselectable.box
          [:p "没有 Microsoft TODO 数据"]]
         ;正常情况下，展示每周计划、每周浇花、每周一学、每天的日报、TO DO 今日、TO DO 历史这几部分
         [:div#todo-info.mx-2.mt-3.is-unselectable.box
          [:<>
           [week/week-plan-add-dialog]
           #_[week/week-plan-log-add-dialog] ;强迫通过日记新建记录
           [:div.mb-5
            ;每周计划卡片，包括本周计划和每周一学
            [:p
             [:span.has-text-weight-bold.is-family-code.dui-tips.mb-2
              {:on-click     #(rf/dispatch [:app/show-modal :add-week-plan!])
               :title "点击新建本周计划项"}
              "本周计划"]
             ;每周一学
             [:span " "]
             (if learn-done
               [:span.has-text-weight-normal.is-size-7.has-text-info.is-clickable.dui-tips
                {:on-click     #(do (rf/dispatch [:dashboard/learn-week-set-today {:non-end true}]))
                 :data-tooltip "没有未完成记录"}
                "每周一学"]
               [:span.has-text-weight-normal.is-size-7.has-text-danger.is-clickable.dui-tips
                {:on-click     #(do (rf/dispatch [:global/notice {:message  "已经完成每周一学任务了吗？"
                                                                  :callback [:dashboard/learn-week-set-today {:end true}]}])
                                    (.open js/window "https://edu.inspur.com" "_blank"))
                 :data-tooltip "未完成学习，点此标记完成"}
                "每周一学!!"])
             [:span " "]
             [:span.has-text-weight-normal.is-size-7.has-text-info.is-clickable.dui-tips
              {:on-click     #(do (rf/dispatch [:dashboard/learn-week-set-today {:start true}]))
               :data-tooltip "新建每周一学请求"}
              "+"]]
            [week/plan-widget week-plans {:go-diary-add-log true}]]
           (for [day days]
             ^{:key day}
             [:<>
              (if (= day (keyword today))
                ;今日的待办事项卡片，包括每天的日报项目
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
                    [:span.has-text-weight-bold.is-family-code "我的一天"]
                    ;每天计划完成百分比
                    #_[:span.has-text-weight-normal
                       (gstring/format " (%s/%s)" finished-count all-count)]
                    ;每周绿萝浇水记录
                    #_[:span.has-text-weight-normal.is-size-6.is-clickable
                       {:on-click #(rf/dispatch [:dashboard/plant-week-set-today])}
                       " "
                       [:<>
                        (for [plant plant-status]
                          ^{:key (random-uuid)}
                          [:<>
                           (if (= plant 1) [:i.fa.fa-pagelines.has-text-success]
                                           [:i.fa.fa-pagelines {:style {:color "#ddd"}}])])]
                       " "]
                    [:span " "]
                    ;每天的日报
                    (if day-work
                      [:span.has-text-weight-normal.is-size-7.has-text-info.is-clickable
                       {:on-click #(do (rf/dispatch [:dashboard/day-work-edit nil])
                                       (rf/dispatch [:dashboard/day-work]))}
                       day-work]
                      [:span.has-text-weight-normal.is-size-7.has-text-danger.is-clickable
                       {:on-click #(do (rf/dispatch [:global/notice {:message  "已经完成日报吗？"
                                                                     :callback [:dashboard/day-work-edit "已完成日报"]}])
                                       (.open js/window "http://10.110.88.102/pro/effort-calendar.html#app=my" "_blank"))}
                       "没有日报"])
                    ;今日的待办事项项目
                    (for [{:keys [title status list] :as todo} data]
                      ^{:key todo}
                      [:p.mt-1
                       [:span.tag.is-small.is-rounded.is-size-7.mr-2.is-white list]
                       [:span.is-size-7 (when (= status "completed")
                                          {:style {:text-decoration :line-through}})
                        title]])])]
                ;非今日的待办事项
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
                       (if (not= status "completed") " ×")]]))])])]])]]]))
