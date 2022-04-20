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
            :uri-fn         #(str "/cyber/dashboard/summary?day=5")
            :data           :dashboard/recent-data
            :clean          :dashboard/recent-data-clean
            :failure-notice true})

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
            :failure-notice true})