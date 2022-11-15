(ns cyberme.client.ios
  (:require [cyberme.cyber.todo :as todo]
            [cyberme.cyber.fitness :as fitness]
            [cyberme.cyber.inspur :as inspur]
            [cyberme.cyber.weather :as weather]
            [clojure.tools.logging :as log]
            [cyberme.cyber.week-plan :as week-plan]
            [cyberme.tool :as tool]
            [cyberme.db.core :as db]
            [clojure.string :as str])
  (:import (java.time LocalDateTime)
           (java.time.format DateTimeFormatter)))

(defn handle-ios-dashboard
  "iOS Dashboard API
  在 handle-dashboard 的基础上添加额外信息：每周计划、每周学习和日报"
  [{:keys [day] :as params :or {day 7}}]
  (let [{:keys [data message status]} (inspur/handle-dashboard params)
        {week-plan :data} (week-plan/handle-get-week-plan)
        week-plan (or week-plan [])
        with-log-week-plan (mapv (fn [plan]
                                   (let [with-logs (if (nil? (:logs plan))
                                                     (assoc plan :logs [])
                                                     plan)
                                         final (if (string? (:progress plan))
                                                 (assoc plan :progress 0.0)
                                                 plan)]
                                     final)) week-plan)
        with-week-plan-data (merge data {:weekPlan with-log-week-plan})]
    (let [this-week (tool/all-week-day)
          week-info (db/day-range {:from (first this-week) :to (last this-week)})
          week-info-map (reduce #(assoc %1 (:day %2) %2) {} week-info)
          full-week-info (map #(get week-info-map % {}) this-week)
          count-learn-req (count (filterv #(-> % :info :learn-request nil? not) full-week-info))
          count-learn-done (count (filterv #(-> % :info :learn-done nil? not) full-week-info))
          learn-done (= count-learn-req count-learn-done)
          with-week-learn-data
          (assoc-in with-week-plan-data [:work :NeedWeekLearn] (not learn-done))]
      (let [if-work-day? (inspur/do-need-work (LocalDateTime/now))
            need-diary-report?
            (if if-work-day?
              (not (str/includes? (or (-> (db/today) :info :day-work) "") "已完成"))
              false)]
        {:message message
         :status status
         :data (assoc-in with-week-learn-data [:work :NeedDiaryReport] need-diary-report?)}))))

(defn handle-ios-widget
  "iOS Widget API"
  [{:keys [kpi token id]}]
  (let [{:keys [OffWork NeedMorningCheck WorkHour SignIn]} (inspur/handle-serve-hint {:token token})
        ;summary (handle-serve-summary {:useAllData true :kpi kpi :token token})
        todo (todo/handle-today {:focus false :showCompleted true})
        ;{:active, :rest, :diet, :goal-active, :goal-cut}
        fitness (fitness/today-active)
        w (weather/get-weather-cache (or (keyword id) :na-tie))]
    (log/info "[iOSWidget] request widget info now...")
    {:weatherInfo     (:weather w)
     :tempInfo        (:temp w)
     :tempFutureInfo  (:tempFuture w)
     :fitnessInfo     fitness
     :workStatus      (cond NeedMorningCheck "🔴"
                            OffWork "🟢"
                            :else "🟡")
     :offWork         (if (nil? OffWork) true OffWork)
     :cardCheck       (let [alter (if WorkHour [(str WorkHour)] [])]
                        (if-let [signin SignIn]
                          (try
                            (mapv (fn [{:keys [^LocalDateTime time]}]
                                    (if (instance? LocalDateTime time)
                                      (.format (DateTimeFormatter/ofPattern "HH:mm") time)
                                      (throw (RuntimeException. "未预期的结果")))) signin)
                            (catch Exception e
                              (log/error "error to parse time from SignIn" e)
                              alter))
                          alter))
     :todo            (or (mapv (fn [item]
                                  {:title      (:title item)
                                   :isFinished (= "completed" (:status item))
                                   :create_at  (:create_at item)})
                                (todo/sort-todo (:tasks todo))) [])
     :needDiaryReport (not (inspur/have-finish-daily-report-today?))
     :needPlantWater  true
     :updateAt        (int (/ (System/currentTimeMillis) 1000))}))