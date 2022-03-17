(ns cyberme.place.request
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [clojure.string :as string]
    [clojure.set :as set]
    [cyberme.event.request :as req]))

;;;;;;;;;;;;;;;;;;;;;;;; 位置 ;;;;;;;;;;;;;;;;;;;;;;
;获取所有位置和物品数据，失败后自动弹出对话框并清空数据
(req/ajax-flow {:call           :place/fetch
            :uri-fn         #(str "/api/places")
            :failure-notice true
            :data           :place/fetch-data
            :clean          :place/clean-data})

;新建位置，失败后由对话框获取数据展示并自行清除
(req/ajax-flow {:call    :place/new
            :is-post true
            :uri-fn  #(str "/api/place")
            :data    :place/new-failure
            :clean   :place/new-clean-failure})

;修改位置
(req/ajax-flow {:call    :place/edit
            :data    :place/edit-callback
            :clean   :place/edit-callback-clean
            :uri-fn  #(str "/api/place/" (:id %))
            :is-post true})

(rf/reg-event-db
  :place/current (fn [db [_ data]] (assoc db :place/current data)))

(rf/reg-sub
  :place/current (fn [db _] (:place/current db)))

(rf/reg-event-db
  :place/current-clean (fn [db _] (dissoc db :place/current)))

;删除位置，数据库返回请求全部显示，如果成功还刷新主界面
(req/ajax-flow {:call                   :place/delete
            :is-post                true
            :uri-fn                 #(str "/api/place/" % "/delete")
            :success-notice         true
            :success-callback-event [:place/fetch]
            :failure-notice         true})
