(ns cyberme.dashboard.request
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [clojure.string :as string]
    [clojure.set :as set]
    [cyberme.util.request :refer [ajax-flow] :as req]))

;;;;;;;;;;;;;;;;;;;;;;;; Dashboard ;;;;;;;;;;;;;;;;;;;;;;
;最近事项
(ajax-flow {:call           :dashboard/recent
            :uri-fn         #(str "/cyber/dashboard/summary?day=17")
            :data           :dashboard/recent-data
            :clean          :dashboard/recent-data-clean
            :failure-notice true})