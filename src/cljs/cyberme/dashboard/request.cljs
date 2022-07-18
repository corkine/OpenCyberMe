(ns cyberme.dashboard.request
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [clojure.string :as string]
    [clojure.set :as set]
    [cyberme.util.request :refer [ajax-flow] :as req]))

;;;;;;;;;;;;;;;;;;;;;;;; Dashboard ;;;;;;;;;;;;;;;;;;;;;;
;Dashboard 核心数据
(ajax-flow {:call           :dashboard/recent
            :uri-fn         #(str "/cyber/dashboard/summary?day=5")
            :data           :dashboard/recent-data
            :clean          :dashboard/recent-data-clean
            :failure-notice true})

;强制刷新 HCM 打卡数据，成功后刷新统计数据
(ajax-flow {:call           :hcm/sync
            :uri-fn         #(str "/cyber/check/now?useCache=false&plainText=false")
            :data           :hcm/sync-data
            :clean          :hcm/sync-data-clean
            :success-callback-event [[:dashboard/recent]]
            :success-notice true
            :failure-notice true})

;每日日报数据
(ajax-flow {:call           :dashboard/day-work
            :uri-fn         #(str "/cyber/dashboard/day-work")
            :is-post        false
            :data           :dashboard/day-work-data
            :clean          :dashboard/day-work-clean
            :failure-notice true})

(ajax-flow {:call           :dashboard/day-work-edit
            :uri-fn         #(str "/cyber/dashboard/day-work")
            :is-post        true
            :data           :dashboard/day-work-edit-data
            :clean          :dashboard/day-work-edit-clean
            :success-callback-event [[:dashboard/day-work]]
            :failure-notice true})

;浇花和学习内容获取二合一接口
(ajax-flow {:call           :dashboard/plant-week
            :uri-fn         #(str "/cyber/dashboard/plant-week")
            :is-post        false
            :data           :dashboard/plant-week-data
            :clean          :dashboard/plant-week-data-clean
            :failure-notice true})

(ajax-flow {:call           :dashboard/plant-week-set-today
            :uri-fn         #(str "/cyber/dashboard/plant-week")
            :is-post        true
            :data           :dashboard/plant-week-set-data
            :clean          :dashboard/plant-week-set-data-clean
            :success-callback-event [[:dashboard/plant-week]]
            :failure-notice true})

(ajax-flow {:call           :dashboard/learn-week-set-today
            :uri-fn         #(str "/cyber/dashboard/learn-week")
            :is-post        true
            :data           :dashboard/learn-week-data
            :clean          :dashboard/learn-week-data-clean
            :success-callback-event [[:dashboard/plant-week]]
            :failure-notice true})