(ns cyberme.cyber.marvel
  (:require [cyberme.db.core :as db]
            [clojure.tools.logging :as log]
            [cyberme.tool :as tool]
            [cuerdas.core :as str])
  (:import (java.time LocalDate LocalDateTime)))

(def start-day (LocalDate/of 1996 3 3))

(defn fetch-marvel
  "从数据库获取配置项"
  []
  (try
    (or (:info (db/someday {:day start-day})) {})
    (catch Exception e
      (log/error "[marvel] fetch failed. " (.getMessage e))
      {})))

(defn set-marvel
  "设置数据库配置项"
  [info]
  (try
    (db/set-someday {:day start-day :info info})
    (catch Exception e
      (log/error "[marvel] insert marvel failed." (.getMessage e)))))

(defn find-body-mass
  "获取减重记录
  {:body-mass {:records {localDateTimeStr Number}
               :start-origin Number
               :start-month Number
               :start-week Number
               :last-record Number}}
  返回格式：{:body-mass-week :body-mass-month :body-mass-origin}"
  ([marvel]
   (let [{{:keys [start-origin start-month start-week
                  last-record records]
           :or   {start-origin 100.0
                  start-month  100.0
                  start-week   100.0
                  last-record  100.0
                  records      {}}} :body-mass} marvel]
     {:body-mass-week   (- start-week last-record)
      :body-mass-month  (- start-month last-record)
      :body-mass-origin (- start-origin last-record)}))
  ([] (find-body-mass (fetch-marvel))))

(defn set-body-mass
  "记录减重"
  [body-mass-date-str body-mass-value]
  (let [body-mass-time (try
                         (if (nil? body-mass-date-str)
                           (LocalDateTime/now)
                           (.atStartOfDay (LocalDate/parse body-mass-date-str)))
                         (catch Exception e
                           (log/error "[marvel] body-mass can't parse " body-mass-date-str e)
                           (LocalDateTime/now)))
        old-marvel (fetch-marvel)
        {{:keys [start-origin start-month start-week
                 last-record records]
          :or   {records {}}} :body-mass} old-marvel]
    (let [new-records (assoc records (str body-mass-time) body-mass-value)
          r-keys (sort (mapv #(LocalDateTime/parse %) (keys new-records)))
          start-origin (if (nil? start-origin)
                         (get new-records (str (first r-keys)))
                         start-origin)
          ;简化逻辑，每次记录都重置 start-month, start-week 和 last-record
          month-start (.atStartOfDay (LocalDate/of ^int (.getYear body-mass-time)
                                                   ^int (.getMonthValue body-mass-time) 1))
          week-start (.atStartOfDay
                       (.minusDays (.toLocalDate body-mass-time)
                                   (- (.getValue (.getDayOfWeek body-mass-time)) 1)))
          month-1 (first (drop-while #(.isBefore % month-start) r-keys))
          week-1 (first (drop-while #(.isBefore % week-start) r-keys))
          start-month (get new-records (str month-1))
          start-week (get new-records (str week-1))]
      (let [new-marvel-body-mass {:start-origin start-origin
                                  :start-month  start-month
                                  :start-week   start-week
                                  :last-record  body-mass-value
                                  :records      new-records}]
        (log/info "[marvel] set marvel to " new-marvel-body-mass)
        (set-marvel (merge old-marvel new-marvel-body-mass))))))

(defn dashboard-set-marvel
  "handle-dashboard 数据重映射，如果获取到的 :clean :HabitCountUntilNow 存在且大于 marvel :clean-max
  或者 :blue :MaxNoBlueDay 存在且大于 marvel :blue-max，那么更新记录，反之则不更新。
  remap 后的 dashboard data 添加了 :clean :MarvelCount 和 :blue :MarvelCount 字段
  ------------------------------------
  handle-dashboard 数据重映射，统计截止到现在的运动卡路里和冥想分钟数。
  "
  [data]
  (try
    (let [{:keys [blue-max clean-max fitness-record]
           :or   {blue-max       0 clean-max 0
                  fitness-record {:acc-active       0
                                  :acc-mindful      0
                                  :received-active  0
                                  :received-mindful 0
                                  :inbox-active     0
                                  :inbox-mindful    0
                                  :inbox-time       (tool/today-morning-sec)}}
           :as   all-old-marvel} (fetch-marvel)
          ;blue (or (-> data :blue :MaxNoBlueDay) 0)
          ;clean (or (-> data :clean :HabitCountUntilNow) 0)
          ;blue-marvel? (> blue blue-max)
          ;clean-marvel? (> clean clean-max)
          ;_ (if (or blue-marvel? clean-marvel?)
          ;    (log/info "[marvel-re-mapping] set new marvel: old b bm c cm is: "
          ;              blue blue-max clean clean-max))
          ]
      ;如果 inbox 并非为本周，则清空所有数据并重新计算
      ;如果 inbox 未过期，则更新 inbox 并计算 inbox + received 并返回
      ;如果 inbox 已过期，则将 inbox 的看作昨天的，加入 received，重新计数 inbox，计算并返回
      (let [{:keys [inbox-time received-active received-mindful
                    inbox-active inbox-mindful acc-active acc-mindful]
             :or   {acc-active 0 acc-mindful 0}} fitness-record
            today-sec (tool/today-morning-sec)
            week-sec (tool/week-first-sec)
            mindful-now (or (-> data :fitness :mindful) 0)
            active-now (or (-> data :fitness :active) 0)
            merged-record
            (if (< inbox-time week-sec)
              {:start            week-sec
               :received-active  0
               :received-mindful 0
               :inbox-active     active-now
               :inbox-mindful    mindful-now
               :inbox-time       today-sec
               :acc-active       (+ received-active inbox-active acc-active)
               :acc-mindful      (+ received-mindful inbox-mindful acc-mindful)}
              (if (> today-sec inbox-time)
                (assoc fitness-record :received-active (+ received-active inbox-active)
                                      :received-mindful (+ received-mindful inbox-mindful)
                                      :inbox-active active-now
                                      :inbox-mindful mindful-now
                                      :inbox-time today-sec)
                (assoc fitness-record :inbox-active active-now
                                      :inbox-mindful mindful-now)))
            returned-record
            {:marvel-active  (+ (:received-active merged-record)
                                (:inbox-active merged-record))
             :marvel-mindful (+ (:received-mindful merged-record)
                                (:inbox-mindful merged-record))
             :acc-active     (:acc-active merged-record)
             :acc-mindful    (:acc-mindful merged-record)}
            new-marvel (assoc all-old-marvel :fitness-record merged-record)]
        (let [with-body-mass-returned-record
              (merge returned-record (find-body-mass new-marvel))]
          (set-marvel new-marvel)
          (-> data (update :fitness merge with-body-mass-returned-record))))
      #_(cond (and blue-marvel? clean-marvel?)
              (set-marvel (assoc all-old-marvel
                            :blue-max blue
                            :clean-max clean
                            :blue-update (inspur/local-date-time)
                            :clean-update (inspur/local-date-time)))
              blue-marvel?
              (set-marvel (assoc all-old-marvel
                            :blue-max blue
                            :blue-update (inspur/local-date-time)))
              clean-marvel?
              (set-marvel (assoc all-old-marvel
                            :clean-max clean
                            :clean-update (inspur/local-date-time)))
              :else :no-marvel-set)
      #_(-> data
            (assoc-in [:blue :MarvelCount] (max blue-max blue))
            (assoc-in [:clean :MarvelCount] (max clean-max clean))))
    (catch Exception e
      (.printStackTrace e)
      (log/error "[marvel-re-mapping] compare and set marvel failed: " (.getMessage e))
      data)))
