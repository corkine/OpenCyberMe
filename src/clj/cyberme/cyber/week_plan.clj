(ns cyberme.cyber.week-plan
  "每周计划：提供 API 读写数据，展示在前端 Dashboard 上
  每次请求 Dashboard 服务时，查询周计划并更新。
  统计数据为每周学习、工作、饮食和锻炼指标，按照条目划分：
  :info
    :plan [{:name '完成 xxx 的学习'
            :id UUID-STRING
            :description
            :category learn/work/fitness/diet
            :progress 0.5
            :logs [{:update LocalDateTime :progress-from :progress-to :name :description :id}]
            :last-update}]
  日记中可提供每天完成每项指标的百分比和子任务项。"
  (:require [clojure.tools.logging :as log]
            [cyberme.db.core :as db]
            [next.jdbc :as jdbc])
  (:import (java.time LocalDate LocalDateTime)
           (java.util UUID)))

(defn first-week-day
  "获取某天所在的周的第一天，作为本周的标记"
  [^LocalDate local-date]
  (.minusDays local-date
              (- (.getValue (.getDayOfWeek local-date)) 1)))

(def plus-years 100)

(defn get-some-week
  "获取某一周的 info，local-date 限定所在的周"
  ([^LocalDate local-date]
   (get-some-week nil local-date))
  ([transaction ^LocalDate local-date]
   (let [data {:day (.plusYears (first-week-day local-date) plus-years)}]
     (if transaction (db/someday transaction data)
                     (db/someday data)))))

(defn set-some-week
  "重置某一周的 info, local-date 限定所在的周"
  ([^LocalDate local-date info]
   (set-some-week nil local-date info))
  ([transaction ^LocalDate local-date info]
   (let [data {:day  (.plusYears (first-week-day local-date) plus-years)
               :info info}]
     (if transaction (db/set-someday transaction data)
                     (db/set-someday data)))))

(defn merge-some-week
  "整合某一周的 info, local-date 限定所在的周"
  ([^LocalDate local-date info]
   (merge-some-week nil local-date info))
  ([trans ^LocalDate local-date info]
   (let [data {:day  (.plusYears (first-week-day local-date) plus-years)
               :info info}]
     (if trans (db/set-someday-info trans data)
               (db/set-someday-info data)))))

(defn delete-some-week
  "删除某一周的条目, local-date 限定所在的周"
  ([^LocalDate local-date]
   (delete-some-week nil local-date))
  ([trans ^LocalDate local-date]
   (let [data {:day (.plusYears (first-week-day local-date) plus-years)}]
     (if trans (db/delete-day trans data)
               (db/delete-day data)))))

(comment
  (db/today)
  (db/someday {:day (LocalDate/now)})
  (db/set-today {:info {:A "B"}})
  (db/set-someday {:day (LocalDate/of 2022 04 01) :info {:A "B"}})
  (db/delete-day {:day (LocalDate/of 2022 04 01)})
  (db/day-range {:from (LocalDate/of 2021 01 01)
                 :to   (LocalDate/of 2022 04 02)}))

(defn handle-get-week-plan
  "获取本周计划"
  []
  (try
    {:message "获取本周计划成功"
     :data    (-> (get-some-week (LocalDate/now)) :info :plan)
     :status  1}
    (catch Exception e
      (log/error "[week-plan] error: " (.getMessage e))
      {:message (str "获取本周计划失败：" (.getMessage e)) :status -1})))

(defn handle-delete-week-plan-all-items
  "删除本周计划和所有项目与每条项目的记录
  因为数据库行只用来记录 plan，因此直接删除数据库行即可"
  []
  (try
    {:message "删除本周计划成功"
     :data    (delete-some-week (LocalDate/now))
     :status  1}
    (catch Exception e
      (log/error "[week-plan] error: " (.getMessage e))
      {:message (str "删除本周计划失败：" (.getMessage e)) :status -1})))

(defn handle-add-week-plan-item
  "添加本周计划新项目
  {:name '完成 xxx 的学习'
  :id UUID-STRING
  :description
  :category learn/work/fitness/diet
  :progress 0.5
  :logs [{:update LocalDateTime :progress-from :progress-to :name :description :id}]
  :last-update}
  至少需要一个 name, category, 可选 description, progress, id"
  [{:keys [name category progress id]
    :or   {progress 0.0
           category "learn"
           id       (.toString (UUID/randomUUID))}
    :as   new-item}]
  (try
    (jdbc/with-transaction
      [t db/*db*]
      (let [now (LocalDate/now)
            items (-> (get-some-week t now) :info :plan)]
        (let [merged-item (merge {:progress    progress
                                  :id          id
                                  :name        name
                                  :category    category
                                  :last-update (LocalDateTime/now)}
                                 new-item)]
          (if (nil? items)
            (set-some-week t now {:plan [merged-item]})
            (set-some-week t now {:plan (conj items merged-item)})))
        {:message (str "新建本周计划项目" name "成功")
         :status  1}))
    (catch Exception e
      (log/error "[week-plan] error: " (.getMessage e))
      {:message (str "新建本周计划项目" name "失败：" (.getMessage e)) :status -1})))

(defn handle-modify-week-plan-item
  "更新某一计划项目，只允许更新 name、description，需要有 id"
  [{:keys [name description id]}]
  (try
    (jdbc/with-transaction
      [t db/*db*]
      (let [now (LocalDate/now)
            items (-> (get-some-week t now) :info :plan)
            current-item (first (filterv #(= id (:id %)) items))]
        (if current-item
          (let [merged-item (merge current-item
                                   {:last-update (LocalDateTime/now)
                                    :name (or name (:name current-item))
                                    :description (or description (:description current-item))})]
            (set-some-week t now {:plan (mapv #(if (= id (:id %)) merged-item %) items)})
            {:message (str "更新本周计划项目" name "成功")
             :status  1})
          {:message (str "更新本周计划项目" name "失败，找不到 id " id) :status  -1})))
    (catch Exception e
      (log/error "[week-plan] error: " (.getMessage e))
      {:message (str "更新本周计划项目" name "失败：" (.getMessage e)) :status -1})))

(defn handle-delete-week-plan-item
  "删除本周计划特定项目和其所有日志。"
  [item-id]
  (try
    (jdbc/with-transaction
      [t db/*db*]
      (let [now (LocalDate/now)
            items (-> (get-some-week t now) :info :plan)
            deleted-items (filterv #(not= item-id (:id %)) items)]
        (if (empty? deleted-items)
          (delete-some-week t now)                          ;最后一条，记录行删除/本周计划
          (set-some-week t now {:plan deleted-items}))      ;删除当前项目，更新本周计划
        {:message (str "删除本周计划项目" item-id "成功")
         :status  1}))
    (catch Exception e
      (log/error "[week-plan] error: " (.getMessage e))
      {:message (str "删除本周计划项目失败：" (.getMessage e)) :status -1})))

(defn handle-add-week-plan-item-log
  "更新本周计划的项目：添加新记录
  {:name '完成 xxx 的学习'
  :id UUID-STRING
  :description
  :category learn/work/fitness/diet
  :progress 0.5
  :logs [{:update LocalDateTime :progress-from :progress-to :name :description :id}]
  :last-update}
  至少需要一个 progress-delta 项目，最好有一个 name 字段，可选 description、id
  会构造 log {:id :name :description :progress-from :progress-to :update}
  会更新本周计划的 progress, logs, last-update 字段"
  [item-id {:keys [id name progress-delta]
            :or   {id             (.toString (UUID/randomUUID))
                   name           "无标题项目"
                   progress-delta "0.0"}
            :as   log-input}]
  (try
    (jdbc/with-transaction
      [t db/*db*]
      (let [progress-delta (Double/parseDouble (str progress-delta))
            log-input (merge log-input
                             {:id             id
                              :name           name
                              :progress-delta progress-delta})
            now (LocalDate/now)
            now-time (LocalDateTime/now)
            items (-> (get-some-week t now) :info :plan)
            current-item (first (filterv #(= item-id (:id %)) items))]
        (if-not current-item
          {:message (str "添加本周计划新项目失败，没有找到此项目。")
           :status  -1}
          (let [{:keys [logs progress]
                 :or   {logs [] progress 0.0}} current-item]
            (if (some #(= (:id %) id) logs)
              {:message (str "添加本周计划新项目失败，数据库已记录此记录。")
               :status  -1}
              (let [progress (if (string? progress) (Double/parseDouble progress) progress)
                    progress-now (+ progress progress-delta)
                    item-log (merge log-input
                                    {:progress-from progress
                                     :progress-to   progress-now
                                     :update        now-time})
                    current-item (merge current-item
                                        {:logs        (conj logs item-log)
                                         :progress    progress-now
                                         :last-update now-time})
                    all-items (mapv (fn [item]
                                      (if (= item-id (:id item))
                                        current-item item))
                                    items)]
                (set-some-week t now {:plan all-items})
                {:message (str "更新本周计划的项目：添加新记录成功。")
                 :status  1}))))))
    (catch Exception e
      (log/error "[week-plan] error: " (.getMessage e))
      {:message (str "更新本周计划的项目：添加新记录失败：" (.getMessage e))
       :status  -1})))

(defn handle-remove-week-plan-item-log
  "更新本周计划的项目记录：删除旧记录，删除后，本周项目进度撤回到原来的值"
  [item-id log-id]
  (try
    (jdbc/with-transaction
      [t db/*db*]
      (let [now (LocalDate/now)
            now-time (LocalDateTime/now)
            items (-> (get-some-week t now) :info :plan)
            {:keys [logs progress]
             :or   {logs [] progress 0.0}
             :as   current-item} (first (filterv #(= item-id (:id %)) items))
            target-log (first (filter #(= (:id %) log-id) logs))]
        (if (nil? target-log)
          {:message (str "本周计划删除项目失败，数据库未记录此项。")
           :status  -1}
          (let [{:keys [name progress-delta]
                 :or   {progress-delta 0.0}} target-log
                progress-now (- progress progress-delta)
                rest-logs (filterv #(not= (:id %) log-id) logs)
                current-item (merge current-item
                                    {:logs        rest-logs
                                     :progress    progress-now
                                     :last-update now-time})
                all-items (mapv (fn [item]
                                  (if (= (:id item) item-id)
                                    current-item item))
                                items)]
            (set-some-week t now {:plan all-items})
            {:message (str "删除项目：" name "成功。")
             :status  1}))))
    (catch Exception e
      (log/error "[week-plan] error: " (.getMessage e))
      {:message (str "删除本周计划项目记录失败：" (.getMessage e)) :status -1})))

