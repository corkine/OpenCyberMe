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
            [cyberme.util.menu :refer [toggle! menu]]
            [goog.string :as gstring]
            [re-frame.core :as rf]
            [cyberme.dashboard.week-plan :as wp]))

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
        {:keys [todo fitness express movie score work]} (:data recent)
        ;;FITNESS
        {:keys [active rest exercise diet mindful marvel-active marvel-mindful
                body-mass-day-30 body-mass-month body-mass-week body-mass-origin
                goal-active goal-cut acc-active acc-mindful]
         :or   {exercise        0 mindful 0 marvel-active 0 marvel-mindful 0
                acc-active      0 acc-mindful 0
                body-mass-month 0 body-mass-day-30 0 body-mass-week 0 body-mass-origin 0}} fitness
        now-cost-energy (- (+ active rest) diet)
        ;;EXPRESS
        express (filterv #(not= (:status %) 0) express)
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
        {:keys [NeedWork OffWork NeedMorningCheck WorkHour SignIn]} work
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
          [chart-1 {:title "运动" :value (min (/ now-cost-energy goal-cut) (/ active goal-active))
                    :start "#EE0000" :stop "#EE9572"
                    :hint  (simple-print {:active      active
                                          :week-active marvel-active
                                          :acc-active  acc-active
                                          :hint        "Apple Watch 记录的每天活动卡路里消耗"})}]]
         [:div {:style {:margin "-10px -30px -40px -30px"}}
          [chart-1 {:title "锻炼" :value (/ exercise 60)
                    :hint  (simple-print {:exercise exercise
                                          :goal     60
                                          :hint     "Apple Watch 记录的每天锻炼分钟数"})}]]
         [:div {:style {:margin "-10px -30px -40px -30px"}}
          [chart-1 {:title "冥想" :value (let [origin (/ mindful 5)] (if (= origin 0) 0.1 origin))
                    :start "#D8BFD8" :stop "#DDA0DD"
                    :hint  (simple-print {:mindful      mindful
                                          :week-mindful marvel-mindful
                                          :acc-mindful  acc-mindful
                                          :hint         "Apple Watch 记录的每天冥想分钟数"})}]]]
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
                 :data-tooltip "同步 HCM"}] " "]
          [:div.tags
           (for [index (range (count SignIn))]
             ^{:key index}
             [:<>
              (let [{:keys [source time]} (get SignIn index)]
                [:span.tag "#" " " (tool/datetime->time time)])])]]
         [:div.is-align-self-center.is-hidden-touch1.px-3
          [:p.mt-2 "30 天减重 "
           [:span.is-size-4.is-family-code {:style {:vertical-align "-4%"}}
            (gstring/format "%.1f" body-mass-day-30)] " Kg"]
          [:p.is-size-7.mb-3.has-text-weight-light "历史累计 "
           (gstring/format "%.1f" body-mass-origin) " Kg"]]
         [:div.is-align-self-center.is-hidden-touch1.px-3 {:style {:margin-left :-10px}}
          [:p.mt-2 "本周已冥想 "
           [:span.is-size-4.is-family-code {:style {:vertical-align "-4%"}} marvel-mindful] " Min"]
          [:p.is-size-7.mb-3.has-text-weight-light "历史累计 " (int acc-mindful) " Min"]]]]
       [:div#week-info.mx-2.box {:style {:margin-bottom :1em
                                         :position      :relative
                                         :border-radius "6px 6px 0 0"}}
        [:div.columns
         (for [day [0 1 2 3 4 5 6]]
           ^{:key day}
           [:div.column
            [:p.mb-1 (condp = day 0 "周一" 1 "周二" 2 "周三" 3 "周四" 4 "周五" 5 "周六" 6 "周日")]
            (let [{:keys [todo active mindful]} (get week-items day)
                  check-fnn (fn [check good bad]
                              (cond (nil? check) ""
                                    check (if (str/blank? good) "" [:p.mr-1 good])
                                    :else (if (str/blank? bad) "" [:p.mr-1 bad])))]
              [:div.is-size-6.is-family-code {:style {:white-space :nowrap}}
               [:div.is-flex.is-flex-wrap-wrap
                (check-fnn todo "✅" "❌")
                (check-fnn active "🔥" "🧀")
                (check-fnn mindful "🧘‍" "🕳")]])])]]
       [:div#progress-info.mx-2.box.px-0.wave.is-flex
        {:style {:margin-bottom :1em
                 :padding-top   :0px
                 :overflow      :hidden
                 :height        :100px
                 :position      :relative
                 :border-radius "0 0 6px 6px"
                 :top           :-40px
                 :box-shadow    "0 .5em 1em -.125em rgba(10,10,10,.1),0 0 0 1px rgba(10,10,10,0)"}}
        [chart-2 {:width show-pass-percent :hint hint :finish show-score-percent}]]
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
           [week/week-plan-modify-item-dialog :dashboard/plant-week]
           #_[week/week-plan-log-add-dialog]                ;强迫通过日记新建和修改记录
           [:div.mb-5
            ;每周计划卡片，包括本周计划和每周一学
            [:p
             [:span.has-text-weight-bold.is-family-code.dui-tips.mb-2
              {:on-click #(rf/dispatch [:app/show-modal :add-week-plan!])
               :title    "点击新建本周计划项"}
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
                       data (filter #(and (not (str/includes? (:list %) "任务"))
                                          (= (:importance %) "high")) data)
                       data (sort (fn [{s1 :status l1 :list c1 :create_at :as a1}
                                       {s2 :status l2 :list c2 :create_at :as a2}]
                                    (cond (= s1 s2)
                                          (if (= l1 l2) (* (compare c1 c2) -1) (compare l1 l2))
                                          (= "completed" s1) 100
                                          (= "completed" s2) -100
                                          :else (compare a1 a2))) data)]
                   [:<>
                    [:span.has-text-weight-bold.is-family-code.is-clickable
                     {:on-click #(.open js/window "https://to-do.live.com/tasks/myday" "_blank")}
                     "我的一天"]
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
                    (for [{:keys [title status list create_at] :as todo} data]
                      ^{:key create_at}
                      [:<>
                       [:p.mt-1 {:style    {:overflow      :hidden
                                            :text-overflow :ellipsis
                                            :white-space   :nowrap}
                                 :title    title
                                 :on-click (partial toggle! create_at)}
                        [:span.tag.is-small.is-rounded.is-size-7.mr-2.is-white list]
                        [:span.is-size-7 (when (= status "completed")
                                           {:style {:text-decoration :line-through}})
                         title]]
                       [menu {:id create_at :padding :1px :actions
                              (mapv (fn [plan]
                                      [(str "添加到 \"" (:name plan) "\"")
                                       #(wp/week-plan-log-add-from-todo todo plan)])
                                    week-plans)}]])])]
                ;非今日的待办事项
                [:div.mb-4 {:style {:opacity 0.5}}
                 (cond (= day (keyword today))
                       [:span.has-text-weight-bold.is-family-code "今天"]
                       (= day (keyword yesterday))
                       [:span.has-text-weight-bold.is-family-code "昨天"]
                       (= day (keyword tomorrow))
                       [:span.has-text-weight-bold.is-family-code "明天"]
                       (= day (keyword tomorrow+1))
                       [:span.has-text-weight-bold.is-family-code "后天"]
                       :else
                       [:<>
                        [:span.has-text-weight-bold.is-family-code day]
                        [:span.is-family-code.is-size-7.ml-1 (tool/day-kw->week day)]])
                 (let [data (get todo day)
                       data (filter #(not (str/includes? (:list %) "任务")) data)]
                   (for [{:keys [title status list create_at] :as todo} data]
                     ^{:key todo}
                     [:p.mt-1 {:style {:overflow      :hidden
                                       :text-overflow :ellipsis
                                       :white-space   :nowrap}
                               :title title}
                      [:span.tag.is-small.is-rounded.is-size-7.mr-2 list]
                      [:span.is-size-7 title]
                      [:span.is-size-7.has-text-weight-light.has-text-danger
                       (if (not= status "completed") " ×")]]))])])]])]]]))
