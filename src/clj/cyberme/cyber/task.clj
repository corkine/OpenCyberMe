(ns cyberme.cyber.task
  "分布式任务模块"
  (:require [cyberme.db.core :as db]
            [clojure.tools.logging :as log]
            [next.jdbc :as jdbc]
            [cyberme.tool :as tool]
            [clojure.string :as str])
  (:import (java.sql Timestamp)
           (java.time LocalDateTime Instant ZoneId)))

(defn ldt->long [ldt]
  (.getTime (Timestamp/valueOf ldt)))

(defn long->ldt [long]
  (LocalDateTime/ofInstant (Instant/ofEpochMilli long) (ZoneId/systemDefault)))

(defn fetch-job
  "下发一个任务，如果没有任务，则失败，找到任务后更新其 job_status 为 dispatched
  更新其 info 中 dispatch_will_return 值为一个时间戳，更新 job_log 添加此次记录。
  其中 job_status 为 queued dispatched success failed
  {:task_id :job_id :job_status
   {:job_info {:job_data :job_rest_try :job_log :job_result :dispatch_will_return}}"
  [task-id bot-id]
  (try
    (log/info "[TASK]" "task" task-id ",bot" bot-id "is request job..")
    (jdbc/with-transaction
      [t db/*db*]
      (if-let [next (db/next-queued-job t {:task_id task-id})]
        (let [now (LocalDateTime/now)
              failed-at (ldt->long (.plusMinutes now 2))
              one-log (format "%s dispatch by bot %s" now bot-id)
              job-rest-try-now (or (-> next :job_info :job_rest_try) 6)
              job-new {:job_id     (:job_id next)
                       :job_status "dispatched"
                       :job_info   {:job_rest_try         job-rest-try-now
                                    :job_log              (conj (or (-> next :job_info :job_log) [])
                                                                one-log)
                                    :dispatch_will_return failed-at}}
              _ (db/update-job t job-new)]
          {:message "下发任务成功。" :data (merge job-new next) :status 1})
        {:message "当前分组没有未分派的任务" :data nil :status 0}))
    (catch Exception e
      (log/error "log req error: " (str e))
      {:message (str "下发任务失败：" e) :data nil :status 0})))

(defn upload-job
  "上传一个完成的任务，job-info 中提取 job_id，is_success，并更新 job_status，其余全部压入 job_result 中
  上传任务不更新 job_info -> job_log，分派时的标记 + 成功的状态足以满足需求。"
  [task-id {:keys [job_id is_success] :as job-info}]
  (try
    (log/info "[TASK]" "uploading task" task-id "job" job-info)
    (if job_id
      (jdbc/with-transaction
        [t db/*db*]
        (let [real-success (if (boolean? is_success) is_success true)
              job-result (dissoc job-info :job_id :is_success)
              append-data {:job_id     job_id
                           :job_status (if real-success "success" "failed")
                           :job_info   {:job_result job-result}}
              _ (db/update-job append-data)]
          {:message "上传任务成功。" :data append-data :status 1}))
      {:message "上传任务失败，缺少 job_id。" :data nil :status 0})
    (catch Exception e
      (log/error "[TASK] 上传任务失败: " (.getMessage e))
      {:message (str "上传任务失败：" e) :data nil :status 0})))

(defn backend-task-routine
  "后台处理失败或超时 job：
  如果 job 状态为 failed，且 job_rest_try 值大于 0，则减小 job_rest_try 值并将 job 状态更新为 queued。
  如果 job 状态为 dispatched，且 job_rest_try 值大于 0 且 dispatch_will_return 的值已超，则
  减小 job-rest-try 值并将 job 状态更新为 queued。
  此外，将 dispatched 但 job_rest_try < 0 的修改为 failed（最后一次尝试也失败）"
  []
  (while true
    (try
      (let [sleep-sec 300]
        (try
          (log/debug "[task-service] starting modifying job status...")
          (doseq [{:keys [job_id job_status job_info] :as job} (db/all-need-retry)]
            (let [try-now (or (:job_rest_try job_info) 0)
                  log-now (or (:job_log job_info) [])]
              (log/info "[TASK:auto]" "modifying job to queued:" job)
              (db/update-job {:job_id     job_id
                              :job_status "queued"
                              :job_info
                              (merge job_info
                                     {:job_rest_try (- try-now 1)
                                      :job_log      (conj log-now
                                                          (format "%s try by routine, last status %s"
                                                                  (LocalDateTime/now)
                                                                  job_status))})})))
          (doseq [{:keys [job_id job_status job_info] :as job} (db/dispatched-and-no-try)]
            (let [log-now (or (:job_log job_info) [])]
              (log/info "[TASK:auto]" "modifying no chance and last failed job:" job)
              (db/update-job {:job_id     job_id
                              :job_status "failed"
                              :job_info
                              (merge job_info
                                     {:job_log (conj log-now
                                                     (format "%s terminated by routine, last status %s"
                                                             (LocalDateTime/now)
                                                             job_status))})})))
          (log/debug "[task-service] end modifying job status, try to sleep sec: " sleep-sec)
          (catch Exception e
            (log/info "[task-service] task routine failed: " (.getMessage e))))
        (Thread/sleep (* 1000 sleep-sec)))
      (catch Exception e
        (log/info "[task-service] task routine failed: " (.getMessage e))))))

(comment
  (db/task-all-jobs {:task_id "2022gk"})
  (db/add-job {:task_id  "2022gk"
               :job_info {:job_data {:ksh "123" :bmxh "456"}}})
  (db/next-queued-job {:task_id "2022gk"})
  (db/update-job {:job_id     1
                  :job_status "dispatched"
                  :job_info   {}})
  (db/update-job {:job_id     1
                  :job_status "success"
                  :job_info   {:job_result {:zf 999}}})
  (db/delete-job {:job_id 1})
  (db/all-need-retry)
  (let [data (slurp "C:\\Users\\mazhangjing\\Desktop\\2022.txt")
        data (mapv (fn [line]
                     (let [list (str/split line #"\t")]
                       {:bmxh (str/trim (first list)) :ksh (str/trim (second list))}))
                   (filter (comp not str/blank?) (str/split-lines data)))]
    (doseq [user data]
      (db/add-job {:task_id "2022gk"
                   :job_info {:job_data user}})
      (Thread/sleep 100))))