(ns cyberme.cyber.dashboard
  (:require [cyberme.cyber.clean :as clean]
            [cyberme.cyber.fitness :as fitness]
            [cyberme.cyber.todo :as todo]
            [cyberme.cyber.express :as express]
            [cyberme.cyber.mini4k :as mini4k]
            [cyberme.cyber.inspur :as inspur]
            [cyberme.db.core :as db]
            [clojure.tools.logging :as log]
            [cyberme.tool :as tool])
  (:import (java.time LocalDate)))

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
             :acc-active (:acc-active merged-record)
             :acc-mindful (:acc-mindful merged-record)}]
        (set-marvel (assoc all-old-marvel :fitness-record merged-record))
        (-> data (update :fitness merge returned-record)))
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

(def fake-blue {:UpdateTime           ""
                :IsTodayBlue          false
                :WeekBlueCount        0
                :MonthBlueCount       0
                :MaxNoBlueDay         10
                :Day120BalanceDay     10
                :MaxNoBlueDayFirstDay ""
                :MarvelCount          10})

(def fake-clean {:MorningBrushTeeth  true
                 :NightBrushTeeth    true
                 :MorningCleanFace   true
                 :NightCleanFace     true
                 :HabitCountUntilNow 10
                 :HabitHint          "1+1?"
                 :MarvelCount        "20"})

(defn handle-dashboard
  "返回前端大屏显示用数据，包括每日 Blue 和 Blue 计数、每日 Fitness 活动、静息和总目标卡路里
  每日 Clean 和 Clean 计数，每日 TODO 列表、正在追踪的快递、正在追踪的美剧，今日自评得分
  以及一个方便生成本周表现的积分系统，其包含了最近一周每天的数据，格式为：
  :blue {UpdateTime IsTodayBlue WeekBlueCount MonthBlueCount
         MaxNoBlueDay MaxNoBlueDayFirstDay}
  :fitness {:active 200 :rest 1000 :diet 300 :goal-active 500}
  :clean {MorningBrushTeeth NightBrushTeeth MorningCleanFace
          NightCleanFace HabitCountUntilNow HabitHint}
  :todo {:2022-03-01 [{title list create_at modified_at
                       due_at finish_at status(finished,notStarted.)
                       importance}]}
  :movie [{name url data(更新列表) last_update}]
  :express [{id name status(0不追踪1追踪) last_update info(最后更新路由)}]
  :work {:NeedWork :OffWork :NeedMorningCheck :WorkHour :SignIn{:source :time}
         :Policy{:exist :pending :success :failed :policy-count}}
  :today 98
  :score {:2022-03-01
           {:blue true
            :fitness {:rest 2000 :active 300 :diet 300}
            :todo {:total 27 :finished 27}
            :clean {:m1xx :m2xx :n1xx :n2xx}
            :today 99}}"
  [{:keys [day] :or {day 7}}]
  (try
    (let [all-week-day (mapv (comp keyword str) (tool/all-week-day))
          ;today (keyword (tool/today-str))
          ;每一个子项都是 {:2022-03-01 xxx}
          ;要合并为 {:2022-03-01 {:blue xxx}}
          ;blue-week (clean/handle-blue-week)
          ;clean-week (clean/handle-clean-week)
          ;score-week (clean/handle-score-week)
          ;{:active, :rest, :stand, :exercise, :mindful, :goal-active, :goal-cut}
          fitness-week (fitness/week-active)
          todo-week (todo/handle-week-static)
          ; 返回的所有数据
          data {:blue    fake-blue #_(clean/handle-blue-show) ;不再计算 Blue 数据，仅提供模拟值
                :clean   fake-clean #_(clean/handle-clean-show {}) ;不再计算 Clean 数据，仅提供模拟值
                ;:today   (get score-week today) ;不再计算 Score 数据
                :fitness (fitness/today-active)
                :todo    (todo/handle-recent {:day day})
                :express (express/recent-express)
                :movie   (mini4k/recent-update {:day day})
                :work    (inspur/get-hcm-hint {})           ;不再计算 Policy 数据
                ;(assoc :Policy (inspur/policy-oneday (inspur/local-date)))
                :score   (reduce #(assoc % (keyword %2)
                                           {
                                            ;:blue    false #_(get blue-week %2)
                                            ;:today   (get score-week %2)
                                            ;:clean   {:MorningBrushTeeth true
                                            ;          :NightBrushTeeth   true
                                            ;          :MorningCleanFace  true
                                            ;          :NightCleanFace    true}
                                            #_(get clean-week %2)
                                            :fitness (get fitness-week %2)
                                            :todo    (get todo-week %2 [])})
                                 {} all-week-day)}]
      {:message "获取数据成功！" :status 1 :data (dashboard-set-marvel data)})
    (catch Exception e
      {:message (str "获取大屏信息失败！" (.getMessage e)) :status 0})))