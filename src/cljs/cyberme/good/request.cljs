(ns cyberme.good.request
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [clojure.string :as string]
    [clojure.set :as set]
    [cyberme.event.request :refer [ajax-flow] :as req]))

;;;;;;;;;;;;;;;;;;;;;;;; 打包 ;;;;;;;;;;;;;;;;;;;;;;
;最近打包和位置，静默写入数据，失败弹窗
(ajax-flow {:call           :recent/fetch
            :uri-fn         #(str "/api/recent")
            :data           :recent/fetch-data
            :clean          :recent/fetch-data-clean
            :failure-notice true})

;新建打包，失败后由对话框获取数据展示并自行清除
(ajax-flow {:call    :package/new
            :is-post true
            :uri-fn  #(str "/api/package")
            :data    :package/new-failure
            :clean   :package/new-clean-failure})

;删除打包，成功和失败后均弹窗提示，成功还刷新主界面，触发打包重新获取
(ajax-flow {:call                   :package/delete
            :is-post                true
            :uri-fn                 #(str "/api/package/" % "/delete")
            :clean                  :package/delete-data-clean
            :success-notice         true
            :success-callback-event [[:package/fetch] [:place/fetch] [:package/delete-data-clean]]
            :failure-notice         true})

;;;;;;;;;;;;;;;;;;;;;;;; 物品 ;;;;;;;;;;;;;;;;;;;;;;
;新建物品，失败后由对话框获取数据展示并自行清除
(ajax-flow {:call    :good/new
            :is-post true
            :uri-fn  #(str "/api/good")
            :data    :good/new-failure
            :clean   :good/new-clean-failure})

;删除物品，数据库返回请求全部显示，如果成功还刷新主界面
(ajax-flow {:call                   :good/delete
            :is-post                true
            :uri-fn                 #(str "/api/good/" % "/delete")
            :data                   :good/delete-data
            :clean                  :good/delete-data-clean
            :success-notice         true
            :success-callback-event [[:place/fetch] [:good/delete-data-clean]]
            :failure-notice         true})

;隐藏物品，数据库返回请求全部显示，如果成功还刷新主界面
(ajax-flow {:call                   :good/hide
            :is-post                true
            :uri-fn                 #(str "/api/good/" % "/hide")
            :data                   :good/hide-data
            :clean                  :good/hide-data-clean
            :success-notice         true
            :success-callback-event [[:place/fetch] [:good/hide-data-clean]]
            :failure-notice         true})

;物品更改位置，数据库返回请求全部显示，如果成功还刷新主界面
(ajax-flow {:call                   :good/move
            :is-post                true
            :data                   :good/move-data
            :clean                  :good/move-data-clean
            :uri-fn                 #(str "/api/good/" (first %) "/move/" (last %))
            :success-notice         true
            :success-callback-event [[:place/fetch] [:good/move-data-clean]]
            :failure-notice         true})

;物品取消打包，数据库返回结果不论成功失败全部显示，并刷新主界面
(ajax-flow {:call                   :good/unbox
            :uri-fn                 #(str "/api/good/" (first %) "/unbox/" (second %))
            :data                   :good/unbox-data
            :clean                  :good/unbox-data-clean
            :success-notice         true
            :success-callback-event [[:place/fetch] [:good/unbox-data-clean]]
            :failure-notice         true})

;物品确定打包，数据库返回结果失败显示，成功则刷新主界面
(ajax-flow {:call                   :good/box
            :uri-fn                 #(str "/api/good/" (first %) "/box/" (second %))
            :data                   :good/box-data
            :clean                  :good/box-data-clean
            :success-callback-event [[:place/fetch] [:good/box-data-clean]]
            :failure-notice         true})

;物品准备打包，数据库返回结果失败显示，成功则刷新主界面
(ajax-flow {:call                   :good/plan
            :uri-fn                 #(str "/api/good/" (first %) "/plan/" (second %))
            :data                   :good/plan-data
            :clean                  :good/plan-data-clean
            :success-callback-event [[:place/fetch] [:good/plan-data-clean]]
            :failure-notice         true})

;修改物品
(ajax-flow {:call    :good/edit
            :data    :good/edit-callback
            :clean   :good/edit-callback-clean
            :uri-fn  #(str "/api/good/" (:id %))
            :is-post true})

(rf/reg-event-db
  :good/current (fn [db [_ data]] (assoc db :good/current data)))

(rf/reg-sub
  :good/current (fn [db _] (:good/current db)))

(rf/reg-event-db
  :good/current-clean (fn [db _] (dissoc db :good/current)))
