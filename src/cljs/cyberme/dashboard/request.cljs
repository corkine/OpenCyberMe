(ns cyberme.dashboard.request
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [clojure.string :as string]
    [clojure.set :as set]
    [cljs-time.core :as t]
    [cyberme.util.request :refer [ajax-flow] :as req]
    [cljs-time.format :as format]
    [reagent.core :as r]
    [cyberme.util.tool :as tool]
    [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;; Dashboard ;;;;;;;;;;;;;;;;;;;;;;
(rf/reg-event-db
  :dashboard/sync-all
  (fn [_ _]
    (rf/dispatch [:dashboard/recent])
    (rf/dispatch [:dashboard/day-work])
    (rf/dispatch [:dashboard/plant-week])))

;Dashboard 核心数据
(ajax-flow {:call           :dashboard/recent
            :uri-fn         #(let [now (t/day-of-week (t/time-now))
                                   ;周一、周二、周三 显示最近 3 天记录
                                   ;周四、周五显示最近 5 天记录
                                   ;周六、周日显示最近 7 天记录
                                   record-show (cond (and (>= now 1) (<= now 3)) 3
                                                     (and (>= now 4) (<= now 5)) 5
                                                     :else 7)]
                               (str "/cyber/dashboard/summary?day=" record-show))
            :data           :dashboard/recent-data
            :clean          :dashboard/recent-data-clean
            :failure-notice true})

;核心数据查询：是否有当日 clean 记录决定记录对话框参数
(rf/reg-sub
  :clean/add-dialog-data
  (fn [db _]
    (let [now (t/time-now) hour (t/hour now)
          {:keys [HabitCountUntilNow MorningBrushTeeth]
           :or   {HabitCountUntilNow 0 MorningBrushTeeth false}}
          (-> db :dashboard/recent-data :data :clean)
          may-last-noon? (<= HabitCountUntilNow 0)
          may-morning? (not MorningBrushTeeth)
          res {:day     (if may-last-noon? -1 0)
               :time    (if may-last-noon? "下午" (if (or may-morning? (<= hour 12)) "上午" "下午"))
               :confirm (if (and (not may-last-noon?) (not may-morning?) (<= hour 12))
                          "撤销" "确定")}]
      res)))

;强制刷新 HCM 打卡数据，成功后刷新统计数据
(ajax-flow {:call                   :hcm/sync
            :uri-fn                 #(str "/cyber/check/now?useCache=false&plainText=false")
            :data                   :hcm/sync-data
            :clean                  :hcm/sync-data-clean
            :success-callback-event [[:dashboard/recent]]
            :success-notice         true
            :failure-notice         true})

;每日日报数据
(ajax-flow {:call           :dashboard/day-work
            :uri-fn         #(str "/cyber/dashboard/day-work")
            :is-post        false
            :data           :dashboard/day-work-data
            :clean          :dashboard/day-work-clean
            :failure-notice true})

(ajax-flow {:call                   :dashboard/day-work-edit
            :uri-fn                 #(str "/cyber/dashboard/day-work")
            :is-post                true
            :data                   :dashboard/day-work-edit-data
            :clean                  :dashboard/day-work-edit-clean
            :success-callback-event [[:dashboard/day-work]]
            :failure-notice         true})

;浇花、每周学习、每周计划多合一接口
(ajax-flow {:call           :dashboard/plant-week
            :uri-fn         #(str "/cyber/dashboard/plant-week")
            :is-post        false
            :data           :dashboard/plant-week-data
            :clean          :dashboard/plant-week-data-clean
            :failure-notice true})

(ajax-flow {:call                   :dashboard/plant-week-set-today
            :uri-fn                 #(str "/cyber/dashboard/plant-week")
            :is-post                true
            :data                   :dashboard/plant-week-set-data
            :clean                  :dashboard/plant-week-set-data-clean
            :success-callback-event [[:dashboard/plant-week]]
            :failure-notice         true})

(ajax-flow {:call                   :dashboard/learn-week-set-today
            :uri-fn                 #(str "/cyber/dashboard/learn-week")
            :is-post                true
            :data                   :dashboard/learn-week-data
            :clean                  :dashboard/learn-week-data-clean
            :success-callback-event [[:dashboard/plant-week]]
            :failure-notice         true})

;周计划范围接口：获取最近几周的周计划（倒序排列）
(ajax-flow {:call                   :dashboard/week-plan-range
            :uri-fn                 #(str "/cyber/week-plan/list-items?range-week=4")
            :data                   :dashboard/week-plan-range-data
            :clean                  :dashboard/week-plan-range-clean
            :failure-notice         true})

;周计划接口：删除、新建项目成功后触发更新主页，其余：列出项目，添加删除记录仅请求 HTTP
(rf/reg-sub
  :dashboard/week-plan
  (fn [db] (-> db :dashboard/plant-week-data :data :week-plan :data)))

(ajax-flow {:call                   :dashboard/week-plan-delete-item
            :uri-fn                 #(str "/cyber/week-plan/delete-item/" %)
            :is-post                true
            :data                   :dashboard/week-plan-delete-item-data
            :clean                  :dashboard/week-plan-delete-item-clean
            :success-callback-event [[:dashboard/plant-week]]
            :failure-notice         true})

(ajax-flow {:call                   :dashboard/week-plan-add-item
            :uri-fn                 #(str "/cyber/week-plan/add-item")
            :is-post                true
            :data                   :dashboard/week-plan-add-item-data
            :clean                  :dashboard/week-plan-add-item-clean
            :success-callback-event [[:app/scroll-to-result]]
            :failure-notice         true})

(ajax-flow {:call           :dashboard/week-plan-list-item
            :uri-fn         #(str "/cyber/week-plan/list-item")
            :is-post        false
            :data           :dashboard/week-plan-list-item-data
            :clean          :dashboard/week-plan-list-item-clean
            :failure-notice true})

(ajax-flow {:call                   :dashboard/week-plan-modify-item
            :uri-fn                 #(str "/cyber/week-plan/modify-item")
            :is-post                true
            :data                   :dashboard/week-plan-modify-item-data
            :clean                  :dashboard/week-plan-modify-item-clean
            :success-callback-event [[:app/scroll-to-result]]
            :failure-notice         true})

(ajax-flow {:call                   :dashboard/week-plan-item-add-log
            :uri-fn                 #(str "/cyber/week-plan/update-item/" (:item-id %) "/add-log")
            :is-post                true
            :data                   :dashboard/week-plan-item-add-log-data
            :clean                  :dashboard/week-plan-item-add-log-clean
            :success-callback-event [[:app/scroll-to-result]]
            :failure-notice         true})

(ajax-flow {:call                   :dashboard/week-plan-item-update-log
            :uri-fn                 #(str "/cyber/week-plan/update-item/" (:item-id %) "/update-log")
            :is-post                true
            :data                   :dashboard/week-plan-item-update-log-data
            :clean                  :dashboard/week-plan-item-update-log-clean
            :success-callback-event [[:app/scroll-to-result]]
            :failure-notice         true})

(ajax-flow {:call                   :dashboard/week-plan-item-delete-log
            :uri-fn                 #(str "/cyber/week-plan/update-item/" (first %) "/remove-log/" (second %))
            :is-post                true
            :data                   :dashboard/week-plan-item-delete-log-data
            :clean                  :dashboard/week-plan-item-delete-log-clean
            :success-callback-event [[:dashboard/plant-week]]
            :failure-notice         true})

;周计划当前展开和选择数据库
(rf/reg-event-db
  :week-plan-db-set
  (fn [db [_ key value]]
    (assoc-in db [:week-plan key] value)))

(rf/reg-event-db
  :week-plan-db-unset
  (fn [db [_ key]]
    (dissoc db :week-plan key)))

;周计划当前展开和选择数据查询
(rf/reg-sub
  :week-plan-db-query
  (fn [db [_ key]]
    (get (:week-plan db) key)))

;核心数据查询：是否有当日日记决定新建周计划项目日志时跳转到的位置 - 新日记 or 当天日记
(rf/reg-sub
  :week-plan/today-diary-exist?
  (fn [db _]
    (> (or (-> db :dashboard/recent-data :data :today) 0) 50)))

;核心数据查询：可能的下一个计划项目日志名（出现在 today To-do 中的项目，前四个字和计划项目名匹配，
;且未添加到过本项目的计划中）
(rf/reg-sub
  :week-plan/may-next-finish-item-log
  (fn [db _]
    (if-let [today-todo-list
             (get (-> db :dashboard/recent-data :data :todo) (keyword (tool/today-str)))]
      (let [current-plan (-> db :week-plan :current-item)
            plan-name (or (:name current-plan) "")
            plan-first-4-words (.substr plan-name 0 4)
            log-names (mapv #(get % :name "") (or (:logs current-plan) []))]
        (let [find-new!
              (filterv #(let [title (get % :title "")]
                          (and (str/starts-with? title plan-first-4-words)
                               (not (some (fn [log-name] (str/includes? log-name title)) log-names))))
                       today-todo-list)]
          ;如果有多个，最推荐已完成的
          (first (sort :status find-new!)))))))

;获取最近笔记
(ajax-flow {:call            :note/last
            :uri-fn          #(str "/cyber/note/last")
            :data            :note/last-data
            :clean           :note/last-data-clean
            :success-notice  true
            :failure-notice  true
            :notice-with-pre true})

;标记当前清洁状况，成功后刷新当前页面
(ajax-flow {:call                   :dashboard/make-clean
            :uri-fn                 (fn []
                                      (if (>= (t/hour (t/time-now)) 18)
                                        (str "/cyber/clean/update?merge=true&nt=true&nf=true")
                                        (str "/cyber/clean/update?merge=true&mt=true&mf=true")))
            :is-post                false
            :data                   :dashboard/make-clean-data
            :clean                  :note/make-clean-data-clean
            :success-notice         true
            :success-callback-event [[:dashboard/recent]]
            :failure-notice         true})

;添加电视追踪
(ajax-flow {:call    :movie/movie-add
            :uri-fn  #(str "/cyber/movie/?name=" (:name %) "&url=" (:url %))
            :is-post true
            :data    :movie/movie-data
            :clean   :movie/movie-data-clean})

;添加快递追踪
(ajax-flow {:call   :express/express-add
            :uri-fn #(str "/cyber/express/track?no=" (:no %) "&note=" (:note %))
            :data   :express/express-data
            :clean  :express/express-data-clean})

;强制刷新 Microsoft TO-DO 数据，成功后刷新统计数据
(ajax-flow {:call                   :dashboard/todo-sync
            :uri-fn                 #(str "/cyber/todo/sync")
            :data                   :dashboard/todo-sync-data
            :clean                  :dashboard/todo-sync-data-clean
            :success-callback-event [[:dashboard/recent]]
            :success-notice         true
            :failure-notice         true})