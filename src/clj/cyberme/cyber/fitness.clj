(ns cyberme.cyber.fitness
  (:require [cyberme.db.core :as db]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [cyberme.tool :as tool]
            [cyberme.cyber.todo :as todo])
  (:import (java.time LocalDate LocalDateTime Duration)
           (java.util Locale)
           (java.time.format DateTimeFormatter)))

(defn json->data [json-data return-map?]
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

(def goal-active 400)

(def goal-cut 400)

(defn recent-active
  "获取最近的活动记录"
  [day]
  (let [data (db/recent-activity {:day day})]
    (map #(update % :date str) data)))

(def todo-filter #(let [{:keys [list status title]} %]
                    (and
                      (and list status title)
                      (str/includes? list "事项")
                      (= status "completed")
                      (str/includes? title "锻炼"))))

(def update-to-do-item (fn [to-do-map] {:active      600
                                        :rest        4000
                                        :diet        100
                                        :from-todo   true
                                        :origin-todo to-do-map}))

(defn today-active-by-todo
  "返回当日从 TO-DO 中获取的健身记录，格式为 {:active :rest :diet :from-todo :origin-todo}"
  []
  (let [todo-info (get (todo/handle-recent {:day 1}) (tool/today-str))
        active-todos (filterv todo-filter todo-info)]
    (if (empty? active-todos)
      {}
      (update-to-do-item active-todos))))

(defn today-active
  "获取今日的活动记录，格式 {:active, :rest, :diet, :goal-active, :goal-cut}"
  []
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
           (today-active-by-todo))))

(defn week-active-by-todo
  "从 Microsoft TODO 待办事项的包含 事项 的列表中获取带有 锻炼 的事项，将此事项的 due_at 看做其日期，如果其已经完成，则看做新条目
  并且和本周实际的 Apple Watch 上传的活动记录合并（优先 Apple Watch 数据）。"
  []
  (let [;格式 {2022-06-06 [{:modified_at LDT, :time LD,
        ;                  :finish_at LDT, :title String,
        ;                  :list String, :status completed/..,
        ;                  :due_at LDT, :create_at LDT, :importance high/..}]}
        todo-map (todo/handle-recent 8)
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

(defn week-active
  "获取本周的活动记录，格式 {:2022-03-01 {:active, :rest, :diet}}"
  []
  (let [recent (recent-active 8)
        all-day (mapv str (tool/all-week-day))
        day-cate-sum (fn [cate day]
                       (or (:sum (first (filter #(and (= (:date %) day)
                                                      (= (:category %) cate))
                                                recent)))
                           0.0))
        week-result-from-watch (reduce #(assoc %1
                                          (keyword %2)
                                          {:active    (day-cate-sum "activeactivity" %2)
                                           :rest      (day-cate-sum "restactivity" %2)
                                           :diet      (day-cate-sum "dietaryenergy" %2)
                                           :from-todo false})
                                       {} all-day)]
    (let [week-result-from-todo (week-active-by-todo)]
      (merge week-result-from-watch
             week-result-from-todo))))

(defn handle-upload [json-data]
  (try
    (let [data (json->data json-data false)
          ;_ (clojure.pprint/pprint data)
          line-in (count data)
          [{count :next.jdbc/update-count}] (db/insert-fitness-batch {:records data})]
      {:message (str "批量上传成功，共 " line-in " 条数据，插入结果：" count)
       :status  1})
    (catch Exception e
      {:message (str "批量上传失败：" (.getMessage e))
       :status  0})))

(defn handle-recent-active [{day :day}]
  (try
    {:message "获取成功"
     :status  1
     :data    (recent-active day)}
    (catch Exception e
      {:message (str "获取失败： " (.getMessage e))
       :status  0})))

(defn handle-list [{:keys [category lastDays limit]
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

(defn handle-delete [{:keys [id]}]
  (try
    (let [{count :next.jdbc/update-count} (db/delete-fitness {:id id})]
      {:message (str "删除 " id " 成功：变动：" count)
       :status  1})
    (catch Exception e
      {:message (str "删除 " id " 失败：" (.getMessage e))
       :status  0})))

(defn handle-details [{:keys [id]}]
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
  (filter #(= (:category %) :sex) (json->data raw true)))