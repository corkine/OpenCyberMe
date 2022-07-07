(ns gk2022
  "此文件配合 src/clj/cyber/task.clj 实现的分布式爬虫服务端实现爬虫服务
  此文件依赖 Firefox WebDriver 进行网页模拟，更多信息参见文件末尾。"
  (:require [etaoin.api :as e]
            [cheshire.core :as json]
            [org.httpkit.client :as client])
  (:gen-class))

(def version
  "version 1.0 2022-7-5 实现了基本浏览器操作和 taskServer 交互接口
  version 1.1 2022-7-6 实现了和服务端的对接，鉴权。")

(def host "http://localhost:3000")
(def user "user101")
(def pass "pass101")

(defonce client-id (str "GK22Client" (+ (rand-int 999) 2000)))

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
                      host "2022gk" client-id user pass)
          req (client/request {:url url :method :get})
          resp @req
          {:keys [message data status]} (json/parse-string (:body resp) true)
          _ (println "fetch user " data)]
      (if (= 0 status)
        (println "Failed to connect to taskServer: " message) data))
    (catch Exception e
      (println "Failed to connect to taskServer: " (.getMessage e)))))

(defn upload-data [job-data]
  (let [url (format "%s/cyber/task/%s/job" host "2022gk")
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

(defn handle-once [driver]
  (let [user (next-user)
        _ (if (nil? user) (throw (RuntimeException. "获取任务失败！")))
        ksh (-> user :job_info :job_data :ksh)
        bmxh (-> user :job_info :job_data :bmxh)
        home "http://www.heao.com.cn/main/html/xxcx/index.aspx?ExamId=3&TypeId=1"]
    (e/go driver home)
    (e/wait driver 0.2)
    (ensure! #(e/exists? driver :contentpage))
    (e/switch-frame driver :contentpage)
    (e/wait driver 0.1)
    (ensure! #(e/exists? driver :mainFrame))
    (e/switch-frame driver :mainFrame)
    (e/wait driver 0.1)
    (e/click driver {:id "bmxhradio"})
    (e/wait driver 0.1)
    (e/fill-human-multi driver {:Ksh ksh :Bmxh bmxh}
                        {:pause-max 0.1 :mistake-prob 0.1})
    (e/wait driver 0.1)
    (e/click driver :TencentCaptcha)
    (ensure! #(e/exists? driver :tcaptcha_iframe))
    (e/switch-frame driver :tcaptcha_iframe)
    (ensure! #(e/exists? driver :tcaptcha_drag_button))
    (println "等待滑动滑块...")
    (ensure! #(not (e/exists? driver :tcaptcha_drag_button)))
    (e/switch-frame-parent driver)
    (ensure! #(e/has-text? driver "验证成功"))
    (e/click driver {:id "QueryBtn"})
    (ensure! #(e/has-text? driver "总分"))
    (let [ksh (e/get-element-text driver {:css "#tabInfo > tbody:nth-child(1) > tr:nth-child(1) > td:nth-child(2)"})
          name (e/get-element-text driver {:css "#tabInfo > tbody:nth-child(1) > tr:nth-child(2) > td:nth-child(2)"})
          id (e/get-element-text driver {:css "#tabInfo > tbody:nth-child(1) > tr:nth-child(3) > td:nth-child(2)"})
          bmxh (e/get-element-text driver {:css "#tabInfo > tbody:nth-child(1) > tr:nth-child(4) > td:nth-child(2)"})
          yw (e/get-element-text driver {:css "#tabInfo > tbody:nth-child(1) > tr:nth-child(5) > td:nth-child(2)"})
          sxjsj (e/get-element-text driver {:css "#tabInfo > tbody:nth-child(1) > tr:nth-child(6) > td:nth-child(2)"})
          zy (e/get-element-text driver {:css "#tabInfo > tbody:nth-child(1) > tr:nth-child(7) > td:nth-child(2)"})
          jc (e/get-element-text driver {:css "#tabInfo > tbody:nth-child(1) > tr:nth-child(8) > td:nth-child(2)"})
          zf (e/get-element-text driver {:css "#tabInfo > tbody:nth-child(1) > tr:nth-child(10) > td:nth-child(2)"})]
      (printf "考生 %s，身份证号 %s，考生号 %s，报名序号 %s，语文 %s，数学/计算机 %s，专业 %s，基础 %s，总分 %s\n" name id ksh bmxh yw sxjsj zy jc zf)
      (upload-data {:name       name :id id
                    :ksh        ksh :bmxh bmxh
                    :yw         yw :sxjsj sxjsj
                    :zy         zy :jc jc :zf zf
                    :job_id     (:job_id user)
                    :is_success true}))))

(defn -main []
  (let [driver (e/firefox {:path-driver  "geckodriver.exe"
                           :path-browser "firefox/firefox.exe"})]
    (println "GK2022 Score Check Client")
    (println version)
    (while true
      (try
        (handle-once driver)
        (Thread/sleep 500)
        (catch Exception e
          (println "执行错误，将在 10s 后重试.." (.getMessage e))
          (Thread/sleep 10000))))))

(comment
  (e/go driver home)
  (e/query driver {:css "form"})
  (e/exists? driver {:id "Ksh"})
  (e/fill driver :Ksh "corine")
  (e/get-element-text driver {:css "input#Ksh"})
  (e/refresh driver)
  (e/scroll-bottom driver)
  (e/scroll-top driver)
  (def driver (e/firefox {:path-driver  "geckodriver.exe"
                          :path-browser "firefox/firefox.exe"}))
  (handle-once driver))