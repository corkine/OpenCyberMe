(ns cyberme.diary.request
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [clojure.string :as string]
    [clojure.set :as set]
    [cyberme.util.request :refer [ajax-flow] :as req]))

;;;;;;;;;;;;;;;;;;;;;;;; Dashboard ;;;;;;;;;;;;;;;;;;;;;;
;最近事项
(ajax-flow {:call           :diary/list
            :uri-fn         #(str "/cyber/diary/list?day=5")
            :data           :diary/list-data
            :clean          :diary/list-data-clean
            :failure-notice true})