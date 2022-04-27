(ns cyberme.work.request
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [clojure.string :as string]
    [clojure.set :as set]
    [cyberme.util.request :refer [ajax-flow] :as req]))

;;;;;;;;;;;;;;;;;;;;;;;;;;; hcm ;;;;;;;;;;;;;;;;;;;;;;;;;;;
(ajax-flow {:call :hcm/month
            :data :hcm/month-data
            :clean :hcm/month-data-clean
            :uri-fn #(str "/cyber/check/month_summary")
            :is-post false
            :failure-notice true})

(ajax-flow {:call :hcm/all
            :data :hcm/all-data
            :clean :hcm/all-data-clean
            :uri-fn #(str "/cyber/check/all_summary")
            :is-post false
            :failure-notice true})

(ajax-flow {:call :hcm/hint
            :data :hcm/hint-data
            :clean :hcm/hint-data-clean
            :uri-fn #(str "/cyber/check/summary?useAllData=true&showDetails=false")
            :is-post false
            :failure-notice true})

(ajax-flow {:call :hcm/todo
            :data :hcm/todo-data
            :clean :hcm/todo-data-clean
            :uri-fn #(str "/cyber/todo/list?listName=%F0%9F%90%A0%20工作&day=20")
            :is-post false
            :failure-notice true})

(rf/reg-event-db
  :set-paste-status
  (fn [db [_ status]]
    (assoc db :paste-status status)))

(rf/reg-event-db
  :set-paste-switch
  (fn [db _]
    (assoc db :paste-switch (not (:paste-switch db)))))

(rf/reg-sub
  :paste-status
  (fn [db _]
    (:paste-status db)))

(rf/reg-sub
  :paste-switch
  (fn [db _]
    (true? (:paste-switch db))))