(ns cyberme.cyber.dashboard
  "前端和 Flutter 大屏 API"
  (:require
    [cyberme.cyber.diary :as diary]
    [cyberme.cyber.express :as express]
    [cyberme.cyber.fitness :as fitness]
    [cyberme.cyber.inspur :as inspur]
    [cyberme.cyber.marvel :refer [dashboard-set-marvel]]
    [cyberme.cyber.graph :as todo]
    [cyberme.media.mini4k :as mini4k]
    [cyberme.tool :as tool]))


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
  :diary {:draft-count 0}
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
          data {;:blue    (clean/handle-blue-show) ;不再计算 Blue 数据，仅提供模拟值
                ;:clean   (clean/handle-clean-show {}) ;不再计算 Clean 数据，仅提供模拟值
                ;:today   (get score-week today) ;不再计算 Score 数据
                :fitness (fitness/today-active)
                :todo    (todo/handle-recent {:day day})
                :express (express/recent-express)
                :movie   (mini4k/recent-update {:day day})
                :work    (inspur/get-hcm-hint {})           ;不再计算 Policy 数据
                :diary   {:draft-count (diary/handle-draft-diary-count)}
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