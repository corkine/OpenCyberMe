(ns zk2022
  "此文件配合 src/clj/cyber/task.clj 实现的分布式爬虫服务端实现爬虫服务。"
  (:require [cheshire.core :as json]
            [org.httpkit.client :as client])
  (:gen-class))

(def version
  "version 1.0 2022-7-5 实现了基本浏览器操作和 taskServer 交互接口
  version 1.1 2022-7-6 实现了和服务端的对接，鉴权。")

(def host "https://cyber.mazhangjing.com")
(def user "temp_user")
(def pass "temp_pass")

(defonce client-id (str "ZK22Client" (+ (rand-int 999) 2000)))

(defn ensure!
  ([fnn]
   (ensure! fnn "等待条件" 60000))
  ([fnn reason]
   (ensure! fnn reason 60000))
  ([fnn reason timeout]
   (if (fnn)
     true
     (if (> timeout 0)
       (do (Thread/sleep 1000)
           (ensure! fnn reason (- timeout 1000)))
       (throw (RuntimeException. ^String (str reason "超时，程序中断。")))))))

(defn next-user []
  (try
    (let [url (format "%s/cyber/task/%s/job?bot=%s&user=%s&secret=%s"
                      host "2022zk" client-id user pass)
          req (client/request {:url url :method :get})
          resp @req
          {:keys [message data status]} (json/parse-string (:body resp) true)
          _ (println "fetch user " data)]
      (if (= 0 status)
        (println "Failed to connect to taskServer: " message) data))
    (catch Exception e
      (println "Failed to connect to taskServer: " (.getMessage e)))))

(defn upload-data [job-data]
  (let [url (format "%s/cyber/task/%s/job" host "2022zk")
        _ (println "uploading " job-data)
        req (client/request {:url   url
                             :method  :post
                             :headers {"user" user "secret" pass
                                       "Content-Type" "application/json"}
                             :body    (json/generate-string job-data)})
        {:keys [message data status]} (json/parse-string (:body @req) true)
        _ (println "uploading result: " message)]
    (if (= 0 status)
      (println "Failed to upload to taskServer: " message)
      (println "Upload to taskServer done!" message))))

(defn fetch! [zkzh sfzh]
  (println "fetch " zkzh sfzh)
  (let [req
        (client/request {:url        "http://61.163.231.50:8811/admin/zzcjk/getBySfzhXm"
                         :method     :post
                         :user-agent "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Safari/537.36 Edg/94.0.992.31"
                         :headers    {"Cookie" "__cf_bm=QLfbwBdATTDQFL5mk6PZ2hlMjJcH5o8S4CsNiq8Rc0M-1632655569-0-AX5Gwf1p8qZYSfnjAOXuPhixrpZUeukw5qJ0ZM2XjTe+hGaRAFprA9ERA1UHcER3XRc0lSABtnOK02KJSfWPyRDxTUYVJpSbgykaspHPj++v2RVDtZ1HRU6MuMgWl6KomA=="
                                      "Content-Type" "Application/json"}
                         :body (json/generate-string {:zkzh zkzh :sfzh (.substring sfzh 14 18)})})
        resp @req
        {:keys [message data]} (json/parse-string (:body resp) true)
        result (-> data :result)]
    (when (= "成功" message) result)))

(defn fetch-one! []
  (let [user (next-user)
        _ (if (nil? user) (throw (RuntimeException. "获取任务失败！")))
        zhzh (-> user :job_info :job_data :zkzh)
        sfzh (-> user :job_info :job_data :sfzh)
        result (fetch! zhzh sfzh)]
    (if result
      (do (println "result: " result)
          (upload-data (merge {:job_id     (:job_id user)
                               :is_success true}
                              result)))
      (throw (RuntimeException. "没有正确返回数据")))))

(defn -main []
  (let []
    (println "K2022 Score Check Client")
    (println version)
    (while true
      (try
        (fetch-one!)
        (Thread/sleep 500)
        (catch Exception e
          (println "执行错误，将在 10s 后重试.." (.getMessage e))
          (Thread/sleep 10000))))))