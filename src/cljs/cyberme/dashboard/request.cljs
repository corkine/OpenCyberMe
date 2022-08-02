(ns cyberme.dashboard.request
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [clojure.string :as string]
    [clojure.set :as set]
    [cljs-time.core :as t]
    [cyberme.util.request :refer [ajax-flow] :as req]
    [cljs-time.format :as format]))

;;;;;;;;;;;;;;;;;;;;;;;; Dashboard ;;;;;;;;;;;;;;;;;;;;;;
(rf/reg-event-db
  :dashboard/sync-all
  (fn [_ _]
    (rf/dispatch [:dashboard/recent])
    (rf/dispatch [:dashboard/day-work])
    (rf/dispatch [:dashboard/plant-week])))

;Dashboard 核心数据
(ajax-flow {:call           :dashboard/recent
            :uri-fn         #(str "/cyber/dashboard/summary?day=5")
            :data           :dashboard/recent-data
            :clean          :dashboard/recent-data-clean
            :failure-notice true})

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

(rf/reg-sub
  :dashboard/week-plan
  (fn [db] (-> db :dashboard/plant-week-data :data :week-plan :data)))

;周计划接口：删除、新建项目成功后触发更新主页，其余：列出项目，添加删除记录仅请求 HTTP
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
            :success-callback-event [[:dashboard/plant-week]]
            :failure-notice         true})

(ajax-flow {:call                   :dashboard/week-plan-list-item
            :uri-fn                 #(str "/cyber/week-plan/list-item")
            :is-post                false
            :data                   :dashboard/week-plan-list-item-data
            :clean                  :dashboard/week-plan-list-item-clean
            :failure-notice         true})

(ajax-flow {:call                   :dashboard/week-plan-item-add-log
            :uri-fn                 #(str "/cyber/week-plan/update-item/"(:item-id %)"/add-log")
            :is-post                true
            :data                   :dashboard/week-plan-item-add-log-data
            :clean                  :dashboard/week-plan-item-add-log-clean
            :success-callback-event [[:dashboard/plant-week]]
            :failure-notice         true})

(ajax-flow {:call                   :dashboard/week-plan-item-delete-log
            :uri-fn                 #(str "/cyber/week-plan/update-item/"(first %)"/remove-log/"(second %))
            :is-post                true
            :data                   :dashboard/week-plan-item-delete-log-data
            :clean                  :dashboard/week-plan-item-delete-log-clean
            :success-callback-event [[:dashboard/plant-week]]
            :failure-notice         true})

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