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
    [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;; 日记 ;;;;;;;;;;;;;;;;;;;;;;
;从 list-data 的 data info tags 获取 labels
(rf/reg-sub
  :diary/all-labels
  (fn [db _]
    (set (remove nil?
                 (flatten
                   (map #(-> % :info :tags)
                        (-> db :diary/list-data :data)))))))

(rf/reg-event-db
  :diary/set-filter
  (fn [db [_ data]]
    (assoc db :diary/filter data)))

(rf/reg-sub
  :diary/filter
  (fn [db _] (:diary/filter db)))

;对 list-data 根据 filter 进行过滤
(rf/reg-sub
  :diary/list-data-filtered
  (fn [db _]
    (let [{:keys [data]} (:diary/list-data db)
          {:keys [labels contains]} (:diary/filter db)
          label-contain-fn #(contains? (-> % :info :tags set) labels)
          ;;TODO contains 过滤
          new-data (if (or (nil? labels) (str/blank? labels))
                     data (filter label-contain-fn data))]
      (vec new-data))))

;最近日记
(ajax-flow {:call           :diary/list
            :uri-fn         #(str "/cyber/diaries")
            :data           :diary/list-data
            :clean          :diary/list-data-clean
            :failure-notice true})

;获取某一日记，失败提示
(ajax-flow {:call           :diary/current
            :uri-fn         #(str "/cyber/diary/" %)
            :data           :diary/current-data
            :clean          :diary/current-data-clean
            :failure-notice true})

;更新某一日记，成功和失败提示，成功跳转到日记列表页面
(ajax-flow {:call                   :diary/update-current
            :uri-fn                 #(str "/cyber/diary/" (:id %))
            :is-post                true
            :data                   :diary/update-current-data
            :clean                  :diary/update-current-data-clean
            :success-notice         true
            :success-callback-event [[:diary/update-current-data-clean]
                                     [:diary/current-data-clean]
                                     [:common/navigate! :diary]]
            :failure-notice         true})

;删除某一日记，失败提示，成功跳转回列表页
(ajax-flow {:call                   :diary/delete-current
            :uri-fn                 #(str "/cyber/diary/" % "/delete")
            :is-post                true
            :data                   :diary/delete-current-data
            :clean                  :diary/delete-current-data-clean
            :success-callback-event [[:diary/delete-current-data-clean]
                                     [:diary/current-data-clean]
                                     [:common/navigate! :diary]]
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