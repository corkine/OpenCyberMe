(ns cyberme.diary.request
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [reitit.frontend.easy :as rfe]
    [reitit.frontend.controllers :as rfc]
    [goog.crypt.base64 :as b64]
    [clojure.string :as string]
    [clojure.set :as set]
    [cyberme.util.request :refer [ajax-flow] :as req]
    [cyberme.diary.core :as diary]
    [cyberme.diary.util :refer [satisfy-date]]
    [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;; 日记 ;;;;;;;;;;;;;;;;;;;;;;
;最近日记
(ajax-flow {:call           :diary/list
            :uri-fn         #(str "/cyber/diaries")
            :is-post        true
            :data           :diary/list-data
            :clean          :diary/list-data-clean
            :failure-notice true})

(ajax-flow {:call           :diary/list-draft
            :uri-fn         #(str "/cyber/diaries-draft")
            :is-post        true
            :data           :diary/list-data
            :clean          :diary/list-data-clean
            :failure-notice true})

;获取某一日记，失败提示
(ajax-flow {:call           :diary/current-by-id
            :uri-fn         #(str "/cyber/diary/by-id/" %)
            :data           :diary/current-by-id-data
            :clean          :diary/current-by-id-data-clean
            :failure-notice true})

;获取某一日记(按照日期，注意可能是一个或者多个)，失败提示
(ajax-flow {:call           :diary/current-by-date
            :uri-fn         #(str "/cyber/diary/by-date/" %)
            :data           :diary/current-by-date-data
            :clean          :diary/current-by-date-data-clean
            :failure-notice true})

;获取某一日记的合并数据和清理接口
(rf/reg-sub
  :diary/current-data
  (fn [db _]
    (or (-> (:diary/current-by-date-data db) :data first)
        (-> (:diary/current-by-id-data db) :data))))

(rf/reg-event-db
  :diary/current-data-clean
  (fn [db _]
    (dissoc (dissoc db :diary/current-by-id-data)
            :diary/current-by-date-data)))

;更新某一日记，成功和失败提示，成功跳转到日记列表页面
(ajax-flow {:call                   :diary/update-current
            :uri-fn                 #(str "/cyber/diary/by-id/" (:id %))
            :is-post                true
            :data                   :diary/update-current-data
            :clean                  :diary/update-current-data-clean
            :success-notice         true
            :success-callback-event [[:diary/update-current-data-clean]
                                     [:diary/current-data-clean]
                                     [:common/navigate! :diary]]
            :failure-notice         true})

;删除某一日记，失败提示，成功跳转回列表页
(ajax-flow {:call                   :diary/delete-current-navigate-to-list
            :uri-fn                 #(str "/cyber/diary/by-id/" % "/delete")
            :is-post                true
            :data                   :diary/delete-current-data
            :clean                  :diary/delete-current-data-clean
            :success-callback-event [[:diary/delete-current-data-clean]
                                     [:diary/current-data-clean]
                                     [:common/navigate! :diary]]
            :failure-notice         true})

(ajax-flow {:call                   :diary/delete-current-navigate-to-draft
            :uri-fn                 #(str "/cyber/diary/by-id/" % "/delete")
            :is-post                true
            :data                   :diary/delete-current-data
            :clean                  :diary/delete-current-data-clean
            :success-callback-event [[:diary/delete-current-data-clean]
                                     [:diary/current-data-clean]
                                     [:common/navigate! :diary nil {:draft true}]
                                     [:dashboard/draft-diary-add -1]]
            :failure-notice         true})

(ajax-flow {:call                   :diary/delete-current-refresh-draft
            :uri-fn                 #(str "/cyber/diary/by-id/" % "/delete")
            :is-post                true
            :data                   :diary/delete-current-data
            :clean                  :diary/delete-current-data-clean
            :success-callback-event [[:diary/delete-current-data-clean]
                                     [:diary/current-data-clean]
                                     [:diary/list-draft]
                                     [:dashboard/draft-diary-add -1]]
            :failure-notice         true})

;新建日记，成功和失败提示，成功刷新列表页
(ajax-flow {:call                   :diary/new
            :uri-fn                 #(str "/cyber/diary-new")
            :is-post                true
            :data                   :diary/new-data
            :clean                  :diary/new-data-clean
            :success-notice         true
            :success-callback-event [[:diary/new-data-clean]
                                     [:common/navigate! :diary]]
            :failure-notice         true})

(ajax-flow {:call                   :diary/new-draft
            :uri-fn                 #(str "/cyber/diary-new")
            :is-post                true
            :data                   :diary/new-data
            :clean                  :diary/new-data-clean
            :success-notice         true
            :success-callback-event [[:diary/new-data-clean]
                                     [:dashboard/draft-diary-add 1]
                                     [:common/navigate! :diary nil {:draft true}]]
            :failure-notice         true})