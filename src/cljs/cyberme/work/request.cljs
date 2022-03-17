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

(ajax-flow {:call :hcm/todo
            :data :hcm/todo-data
            :clean :hcm/todo-data-clean
            :uri-fn #(str "/cyber/todo/list?listName=%F0%9F%90%A0%20INSPUR&day=20")
            :is-post false
            :failure-notice true})