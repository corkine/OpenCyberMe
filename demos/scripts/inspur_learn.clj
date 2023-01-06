#!/usr/bin/env bb -Sdeps '{:paths ["."] :deps {etaoin/etaoin {:mvn/version,"1.0.39"}}}'
#!/usr/bin/env bb clojure -Sdeps '{:paths ["."] :deps {etaoin/etaoin {:mvn/version,"1.0.39"}}}' -M
(ns inspur-learn
  "每周一学自动化，需要暴露变量 EDU_INSPUR_PASS，格式为 {username}::{password}
  或提供 config.edn 文件。"
  (:require [clojure.string :as str]
            [etaoin.api :as e]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import (java.time Duration LocalTime)
           (java.util Base64)))

(def config (if (.exists (io/file "config.edn"))
              (edn/read-string (slurp "config.edn"))
              (merge
                (if-let [pass-env (System/getenv "EDU_INSPUR_PASS")]
                  (let [pass (.split (String. (.decode (Base64/getDecoder) pass-env)) "::")]
                    {:user (first pass) :pass (second pass)})
                  {:user "xxx@xxx.com"
                   :pass "YOUR_PASS_HERE"})
                {:path-driver (if (= "SCI" (-> *clojure-version* :qualifier))
                                "../resources/chromedriver.exe"
                                "resources/chromedriver.exe")})))

(defn wait-seconds [seconds stop]
  (let [c (atom seconds)]
    (while (and (> @c 0) (not @stop))
      (Thread/sleep 1000)
      (swap! c dec))))

(defn go-home [driver]
  (e/go driver "https://edu.inspur.com")
  (e/wait-visible driver {:id :btnLogin2})
  (e/fill driver {:id :txtUserName2} (:user config))
  (e/fill driver {:id :txtPassword2} (:pass config))
  (e/click driver {:id :chkloginpass})
  (e/click driver {:id :btnLogin2})
  (e/wait-visible driver {:id :panel2})
  (e/wait driver 2)
  (println "task count: " (e/get-element-text driver {:id :panel2})
           ", exam count: " (e/get-element-text driver {:id :panel3}))
  #_(if-let [res (e/get-element-text driver {:id :panel2})]
      (if (= "0" res)
        (println "task count 0, exit now!")
        (do (println "finding task count" res ", exam count" exam-count)
            (e/click driver {:id :panel2})))
      (println "no task found! exit now!")))

(defn goto-learn-page [driver]
  ;随机漫步
  (e/click driver {:css "#divContents > div > div.banner-bg > div > div.remind.fr > div.infor-wrap > div > ul > li:nth-child(2) > a > div > div.text-trim.gray3"})
  (e/wait-visible driver {:id :StudyPersonCount0})
  (e/click driver {:id :StudyPersonCount0}))

(defn learn-doc [driver need-learn-minute block?]
  (let [control (atom false)]
    (let [need-learn
          (fn [] (let [tasks (filterv #(not (str/includes? (or (e/get-element-text-el driver %) "") "已完成"))
                                      (e/query-all driver [{:tag :ul :class "el-kng-img-list clearfix"} {:tag :li}]))]
                   (if (empty? tasks)
                     (do
                       (println "empty tasks this page, go next...")
                       (e/click driver {:class "pagetext"})
                       (e/wait driver 1)
                       (recur))
                     tasks)))
          business
          (fn [] (let [start-learn (LocalTime/now)]
                   (while true
                     (e/switch-window-next driver)
                     (e/refresh driver)
                     (let [task (first (need-learn))
                           passed-minutes (.toMinutes (Duration/between start-learn (LocalTime/now)))]
                       (cond (> passed-minutes need-learn-minute)
                             (throw (RuntimeException. "已完成学习"))
                             @control
                             (throw (RuntimeException. "停止学习"))
                             :else
                             (do (println "starting learning "
                                          (str/replace (e/get-element-text-el driver task) "\n" ""))
                                 (e/click-el driver task)
                                 (println "waiting for new window...")
                                 (e/switch-window-next driver)
                                 (while (and (not= "100%" (e/get-element-text driver {:id :ScheduleText}))
                                             (not @control))
                                   (e/wait driver 1)
                                   (println "waiting need time: " (e/get-element-text driver {:id :spanLeavTimes}))
                                   (when (e/exists? driver {:id :reStartStudy})
                                     (println "skipping hint...")
                                     (e/click driver {:id :reStartStudy}))
                                   (wait-seconds 30 control))
                                 (e/close-window driver)
                                 (println "stop this task learn...")))))))]
      (if block?
        (business)
        (future (business)))
      control)))

(let [driver (e/chrome (merge {:path-driver (:path-driver config) :log-level :warn}
                              (if-let [chrome (:chrome config)] {:path-browser chrome} {})))]
  (go-home driver)
  (goto-learn-page driver)
  (learn-doc driver 60 true))

(comment
  (def driver (e/chrome (merge {:path-driver (:path-driver config) :log-level :warn}
                               (if-let [chrome (:chrome config)] {:path-browser chrome} {}))))
  (go-home driver)
  (goto-learn-page driver)
  (def stop? (learn-doc driver 60))
  (reset! stop? true)
  )

(comment
  ;todo 暂时只处理一个元素
  (e/wait-visible driver {:id :contentitem1})               ;一定有一个任务
  (e/click driver {:id :contentitem1})
  (e/switch-window-next driver)
  (def all-task (->> (e/query-tree driver {:class :hand})
                     (map #(e/get-element-text-el driver %))))
  (doseq [ele (e/query-all driver {:class :hand})]
    (let [process (e/get-element-text-el driver ele)
          finished? (and (not (nil? process))
                         (.endsWith (.trim process) "100%"))]
      (when-not finished?
        (println "now starting learn " (.replace process "\n" ""))
        (e/click-el driver ele))))
  (e/switch-window-next driver)
  (let [process (if-let [process (e/get-element-text driver {:id :lblStudySchedule})]
                  (try
                    (Integer/parseInt process)
                    (catch Exception e
                      (println "failed to parse process," (.getMessage e))
                      0))
                  0)]
    (if (< process 100)
      (do (println "now process is" process ",continue learning...")
          (e/click driver :btnStartStudy))
      (println "error: this class is finished: process is" process)))

  (while (not (= "100%" (e/get-element-text driver {:id :ScheduleText})))
    (format "当前视频 [%s] 进度 %s，预计剩余时间 %s\n"
            (e/get-element-text driver {:id :lblTitle})
            (e/get-element-text driver {:id :ScheduleText})
            (let [remain (e/get-element-text driver {:id :spanLeavTimes})]
              (if (str/blank? remain)
                "0 分钟" remain)))
    (Thread/sleep 5000))

  (e/click driver {:id :divGoBack}))