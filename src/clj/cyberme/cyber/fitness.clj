(ns cyberme.cyber.fitness
  "HealthKit 健身数据管理（包括快捷指令和 iOS HealthKit API）"
  (:require [cyberme.db.core :as db]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [cyberme.tool :as tool]
            [cyberme.cyber.graph :as todo]
            [next.jdbc :as jdbc])
  (:import (java.time LocalDate LocalDateTime Duration)
           (java.util Locale)
           (java.time.format DateTimeFormatter)))

; clean -> last-day-active 120
; inspur -> today-active, week-active
; route -> 增删改查、上传接口

(def goal-active 500)

(def goal-cut 500)

(def todo-filter #(let [{:keys [list status title]} %]
                    (and
                      (and list status title)
                      (str/includes? list "事项")
                      (= status "completed")
                      (str/includes? title "锻炼"))))

;;;;;;;;; 实现 ;;;;;;;;

(defn- db-save
  "保存数据到数据库"
  ([t ^LocalDate day, info] (db/set-someday-info t {:day day :info info}))
  ([t info] (db-save t (LocalDate/now) info)))

(defn- db-fetch
  "从数据库获取数据"
  ([^LocalDate day] (db/someday {:day (LocalDate/now)}))
  ([] (db/today)))

(defn- db-recent
  "获取最近 day 天的数据"
  [day]
  (let [now (LocalDate/now)
        day (- day 1)]
    (db/day-range {:from (.minusDays now day) :to now})))

(def update-to-do-item (fn [to-do-map] {:active      600
                                        :rest        4000
                                        :diet        100
                                        :from-todo   true
                                        :origin-todo to-do-map}))

(defn- upload-from-shortcut [json-data return-map?]
  (let [data (json/parse-string json-data true)
        need-keys (keys data)
        #_[:activeactivity
           :distance
           :restactivity
           :floor
           :heart
           :restheart
           :walkheart
           :heartvariability]
        _ (Locale/setDefault (Locale/SIMPLIFIED_CHINESE))
        str->time #(try
                     (if (str/blank? %)
                       (LocalDateTime/now)
                       (LocalDateTime/parse (str %) (DateTimeFormatter/ofPattern "yyyy年M月d日 ah:mm")))
                     (catch Exception e
                       (log/warn "can't parse " (str %) ", e: " (.getMessage e))
                       (LocalDateTime/now)))
        value->double #(try
                         (cond (= % "未指定") 0.01
                               (str/blank? %) 0.0
                               :else (Double/parseDouble %))
                         (catch Exception e
                           (log/warn (str "error parse " % ", exception: " (.getMessage e)))
                           0.01))
        duration->seconds #(try                             ;空
                             (let [line (str/split % #":")]
                               (cond (= (count line) 1) 0
                                     (= (count line) 2)
                                     (+ (Integer/parseInt (second line))
                                        (* 60 (Integer/parseInt (first line))))
                                     (= (count line) 3)
                                     (+ (Integer/parseInt (last line))
                                        (* 60 (Integer/parseInt (second line)))
                                        (* 60 60 (Integer/parseInt (first line))))
                                     :else (do (log/warn "not except value: " %)
                                               0)))
                             (catch Exception e
                               (log/warn "not except value: " % ", message: "
                                         (.getMessage e))))
        hash-fn (fn [category start-str value-str]
                  ;对于 active 和 rest，hash 基于 category 和 start 时间，因为一旦生成一个小时的数据
                  ;其不会再变化，不基于 end 时间原因是存在当前一个小时数据不完全的情况，这时基于 start 时间
                  ;hash 将冲突并且能够覆盖。
                  ;对于 diet 而言，薄荷健康当前策略是生成 0 点的多条数据，iOSUpload 将其合为一插入，每天只有
                  ;一条数据，因此基于 category 和 start 时间，每次更新数据都能覆盖。
                  (str (.hashCode (str category (str start-str) (str value-str)))))
        full-data (reduce #(let [{:keys [start value end unit duration]} (get data %2)
                                 lines (fn [data] (str/split-lines data))
                                 category %2]
                             (into %
                                   (mapv (fn [s v e u d]
                                           (if return-map?
                                             {:start    (str->time s)
                                              :value    (value->double v)
                                              :end      (str->time e)
                                              :unit     u
                                              :duration (duration->seconds d)
                                              :category category}
                                             ;category, value, unit, start, "end", duration, hash
                                             [(name category) (value->double v) u (str->time s)
                                              (str->time e) (duration->seconds d)
                                              (hash-fn category s v)]))
                                         (lines start) (lines value)
                                         (lines end) (lines unit) (lines duration)))) [] need-keys)]
    (if return-map?
      (filter #(not= 0.0 (:value %)) full-data)
      (filter #(not= 0.0 (second %)) full-data))))

(defn- recent-active
  "获取最近的活动记录（数据库）"
  [day]
  (let [data (db/recent-activity {:day day})]
    (map #(update % :date str) data)))

(defn- today-active-by-todo
  "返回当日从 TO-DO 中获取的健身记录，格式为 {:active :rest :diet :from-todo :origin-todo}"
  []
  (let [todo-info (get (todo/handle-recent {:day 1}) (tool/today-str))
        active-todos (filterv todo-filter todo-info)]
    (if (empty? active-todos)
      {}
      (update-to-do-item active-todos))))

(defn- recent-active-by-todo
  "从 Microsoft TODO 待办事项的包含 事项 的列表中获取带有 锻炼 的事项，将此事项的 due_at 看做其日期，如果其已经完成，则看做新条目
  并且和本周实际的 Apple Watch 上传的活动记录合并（优先 Apple Watch 数据）。"
  [day]
  (let [;格式 {2022-06-06 [{:modified_at LDT, :time LD,
        ;                  :finish_at LDT, :title String,
        ;                  :list String, :status completed/..,
        ;                  :due_at LDT, :create_at LDT, :importance high/..}]}
        todo-map (todo/handle-recent {:day day})
        update-to-do-item (fn [to-do-map] {:active      (+ goal-active 500)
                                           :rest        (+ goal-active 10000)
                                           :diet        100
                                           :from-todo   true
                                           :origin-todo to-do-map}) ;伪造数据
        ;update-to-do-item (fn [to-do-map] to-do-map) ;原样返回
        fitness-kv-list (reduce (fn [agg [date todos]]
                                  (let [fitness-records (filterv todo-filter todos)]
                                    (if-not (empty? fitness-records)
                                      (assoc agg (keyword date) (update-to-do-item fitness-records))
                                      agg))) {} (seq todo-map))
        ;格式同 to do-map，日期键变为 keyword，条目仅包含满足 to do-filter 的项目(经过 update-to-do-item 映射)，不包含空键
        fitness-map (into {} fitness-kv-list)]
    fitness-map))

(defn- recent-active-todo-and-db
  "获取最近的活动记录(fitness 数据库)，格式 {:2022-03-01 {:active, :rest, :diet}}
  need-compute-day 需要传入 LocalDate 数组，表示计算的日期，
  day-count 需要传入 int，表示距今为止的时长，
  因为计算本周数据时，need-compute-day 包括未来空数据，day-count 则截止到今天为止"
  [need-compute-day day-count]
  (let [recent (recent-active (+ day-count 1))
        all-day (mapv str need-compute-day)
        day-cate-sum (fn [cate day]
                       (or (:sum (first (filter #(and (= (:date %) day)
                                                      (= (:category %) cate))
                                                recent)))
                           0.0))
        recent-result-from-watch (reduce #(assoc %1
                                            (keyword %2)
                                            {:active    (day-cate-sum "activeactivity" %2)
                                             :rest      (day-cate-sum "restactivity" %2)
                                             :diet      (day-cate-sum "dietaryenergy" %2)
                                             :from-todo false})
                                         {} all-day)]
    (merge recent-result-from-watch
           (recent-active-by-todo day-count))))

(defn- recent-active-by-todo-pick
  "获取最近的待办中的健身记录，并且只允许出现在 date-key-set LocalDate Set 中的日期"
  [date-key-set day-count]
  (let [data (recent-active-by-todo day-count)]
    (filter #(contains? date-key-set (first %)) data)))

(defn- recent-active-todo-and-new-db
  "获取最近的活动记录(diary 数据库)，格式 {:2022-03-01 {:active, :rest, :diet}}
  need-compute-day 需要传入 LocalDate 数组，表示计算的日期，
  day-count 需要传入 int，表示距今为止的时长，
  因为计算本周数据时，need-compute-day 包括未来空数据，day-count 则截止到今天为止"
  [need-compute-day day-count]
  (let [recent (db-recent day-count)
        recent-map (into {} (map (fn [data] [(:day data) data]) recent))
        recent-result-from-watch
        (reduce #(assoc %1
                   (keyword (str %2))
                   (if-let [health-info (:health-info (:info (get recent-map %2)))]
                     ;包括任何 HealthKit 上传的健康字段，比如 mindful
                     (merge (dissoc health-info [:activeEnergy :basalEnergy
                                                 :standTime :exerciseTime])
                            {:active    (:activeEnergy health-info)
                             :rest      (:basalEnergy health-info)
                             :stand     (:standTime health-info)
                             :exercise  (:exerciseTime health-info)
                             :from-todo false})
                     {:active    0.0
                      :rest      0.0
                      :stand     0
                      :exercise  0
                      :from-todo false}))
                {} need-compute-day)]
    (merge recent-result-from-watch
           (recent-active-by-todo-pick
             (set need-compute-day) day-count))))

;;;;;;;;; 内部接口 ;;;;;;;;

(defn today-active
  "获取今日的活动记录，整合了数据库和 TODO 待办。
  如果 origin? 为 true，则使用原始数据库(fitness)，反之则使用新数据库(diary)
  返回数据格式 {:active, :rest, :diet, :goal-active, :goal-cut}"
  ([origin?]
   (let [by-todo (today-active-by-todo)]
     (if origin?
       (let [recent (recent-active 1)
             today (str (LocalDate/now))
             in-cat (fn [cate]
                      (filter #(and (= (:date %) today)
                                    (= (:category %) cate))
                              recent))]
         (merge {:active      (or (:sum (first (in-cat "activeactivity"))) 0.0)
                 :rest        (or (:sum (first (in-cat "restactivity"))) 0.0)
                 :diet        (or (:sum (first (in-cat "dietaryenergy"))) 0.0)
                 :goal-active goal-active
                 :goal-cut    goal-cut}
                by-todo))
       (let [{{:keys [activeEnergy basalEnergy standTime exerciseTime] :as health-info}
              :health-info} (:info (db-fetch))
             result-v2 {:active      (or activeEnergy 0.0)
                        :rest        (or basalEnergy 0.0)
                        :stand       (or standTime 0)
                        :exercise    (or exerciseTime 0)
                        :goal-active goal-active
                        :goal-cut    goal-cut}
             ;任何其他 :health-info 中可能存在的数据
             result-v3 (merge (dissoc health-info
                                      :activeEnergy
                                      :basalEnergy
                                      :standTime
                                      :exerciseTime)
                              result-v2)]
         (merge result-v3 by-todo)))))
  ([] (today-active false)))

(defn week-active
  ([origin?]
   ((if origin?
      recent-active-todo-and-db
      recent-active-todo-and-new-db)
    (tool/all-week-day)
    (.getValue (.getDayOfWeek (LocalDate/now)))))
  ([] (week-active false)))

(defn last-day-active
  ([day origin?]
   ((if origin?
      recent-active-todo-and-db
      recent-active-todo-and-new-db)
    (tool/all-day day) day))
  ([day]
   (last-day-active day false)))

;;;;;;;;; REST API 接口 ;;;;;;;;

(defn handle-shortcut-upload
  "处理从 iOS 捷径上传的健身数据"
  [json-data]
  (try
    (let [data (upload-from-shortcut json-data false)
          line-in (count data)
          [{count :next.jdbc/update-count}] (db/insert-fitness-batch {:records data})]
      {:message (str "批量上传成功，共 " line-in " 条数据，插入结果：" count)
       :status  1})
    (catch Exception e
      {:message (str "批量上传失败：" (.getMessage e))
       :status  0})))

(defn handle-ios-app-active-upload
  "上传近期的健身记录，数据包括：
  [{:time yyyy-MM-dd
    :activeEnergy double,kcal
    :basalEnergy double,kcal
    :standTime int,minutes
    :exerciseTime int,minutes
    :ANY OTHER KIND FITNESS INFORMATION HERE}]"
  [logs]
  (log/info "[HealthUploader] upload from iOS " logs)
  (try
    (jdbc/with-transaction
      [t db/*db*]
      (doseq [log logs]
        (db-save t
                 (if (:time log) (LocalDate/parse (:time log))
                                 (LocalDate/now))
                 {:health-info (dissoc log :time)}))
      {:message (str "批量上传成功，共 " (count logs) " 天的数据。")
       :status  1})
    (catch Exception e
      {:message (str "批量上传失败：" (.getMessage e))
       :status  0})))

(defn handle-recent-active
  "获取最近 day 天的多种类别的健身记录"
  [{day :day}]
  (try
    {:message "获取成功"
     :status  1
     :data    (recent-active day)}
    (catch Exception e
      {:message (str "获取失败： " (.getMessage e))
       :status  0})))

(defn handle-list
  "获取某种类别在最近 lastDays 天的，限制获取 limit 条数的健身记录"
  [{:keys [category lastDays limit]
    :or   {lastDays 10 limit 200}}]
  (try
    (if category
      (db/all-fitness-by-cat-after-limit
        {:limit    limit
         :day      (.minusDays (LocalDate/now) lastDays)
         :category category})
      (db/all-fitness-after-limit
        {:limit limit
         :day   (.minusDays (LocalDate/now) lastDays)}))
    (catch Exception e
      {:message (str "列表失败：" (.getMessage e))
       :status  0})))

(defn handle-delete
  "删除某条健身记录"
  [{:keys [id]}]
  (try
    (let [{count :next.jdbc/update-count} (db/delete-fitness {:id id})]
      {:message (str "删除 " id " 成功：变动：" count)
       :status  1})
    (catch Exception e
      {:message (str "删除 " id " 失败：" (.getMessage e))
       :status  0})))

(defn handle-details
  "获取某条健身记录"
  [{:keys [id]}]
  (try
    {:message (str "列出 " id " 成功")
     :data    (db/details-fitness {:id id})
     :status  1}
    (catch Exception e
      {:message (str "列出 " id " 失败：" (.getMessage e))
       :status  0})))

(comment
  (db/delete-fitness {:id 1})
  (db/details-fitness {:id 1})
  (db/all-fitness {:limit 100})
  (db/all-fitness-after {:day (.minusDays (LocalDate/now) 3)})
  (db/all-fitness-after-limit
    {:limit 100
     :day   (.minusDays (LocalDate/now) 3)})
  (db/insert-fitness-batch
    {:records [["test1" "seconds" (LocalDateTime/now)
                (LocalDateTime/now) 1000 "HASHTEST"]]})
  (user/create-migration "fitness")
  (filter #(= (:category %) :sex) (upload-from-shortcut raw true)))