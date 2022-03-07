(ns cyberme.inspur.core
  (:require [org.httpkit.client :as client]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:import (java.time LocalDateTime LocalDate DayOfWeek LocalTime Duration Period)
           (java.time.format DateTimeFormatter)))

(def date-time (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))

(def data-url "https://wx56b5ebcd0c7759e2.hcmcloud.cn/api/attend.view.employee.day")

(def login-url "https://wx56b5ebcd0c7759e2.hcmcloud.cn/login?v=1624152319627&next=%2Fservice%23%2Fattend%2Fview&app_type=service")

(def example-token "2|1:0|10:1646563780|5:token|56:NTgyNmExMDY2YzA5NDBhMTQ0NzBmZWQyNTViMTA1MjU5M2UwYzA1Yw==|1cf208bbc7baf4be4f6a9a976c7566bc55a30e478e6fb2ae14df00e81475920f")

(defonce cache (atom {}))

(defn hcm-info-from-cache [^String date]
  (get @cache (keyword date)))

(defn set-hcm-cache [^String date, info]
  (swap! cache assoc (keyword date) info))

(defn get-hcm-info [{:keys [^LocalDateTime time ^String token]}]
  "根据 Token 和时间从 HCM 服务器解析获取签到数据，返回 {:data :message}"
  (let [time (if (nil? time) (-> (LocalDateTime/now) (.format DateTimeFormatter/ISO_LOCAL_DATE))
                             (.format time DateTimeFormatter/ISO_LOCAL_DATE))
        cache-res (hcm-info-from-cache time)]
    (if-not cache-res
      (try
        (let [token (if (str/blank? token) example-token token)
              req (client/request {:url     data-url
                                   :method  :post
                                   :body    (format "{\"employee_id\":\"\",\"date\":\"%s\"}" time)
                                   :headers {"Cookie" (str "token=\"" token "\"")}})
              {:keys [status body] :as full-resp} @req
              _ (when (not= status 200)
                  (do (log/info "[hcm-request] response: " full-resp)
                      (throw (RuntimeException. "服务器未正确返回数据，可能是登录过期"))))
              info {:data    (json/parse-string body true)
                    :message "获取 HCM INFO 数据成功"
                    :status  1}
              _ (do (log/info "[hcm-request] cached data")
                    (set-hcm-cache time info))]
          info)
        (catch Exception e
          {:message (str "get-hcm-info failed：" (.getMessage e))
           :status  0}))
      (do (log/info "[hcm-request] get from cache hint!")
          (update cache-res :message #(str % " (from cache)"))))))

(defn signin-data [hcm-info]
  "从 HTTP 返回数据中解析签到数据：
  [{:source 武汉江岸区解放大道,
    :time #object[java.time.LocalDateTime 2022-03-05T09:30:44]}]"
  (let [signin-vec (-> hcm-info :data :result :data :signin)
        pure-sign-vec (mapv (comp (fn [{time-str :time :as origin}]
                                    (assoc origin :time (LocalDateTime/parse time-str date-time)))
                                  #(select-keys % [:source :time])) signin-vec)]
    pure-sign-vec))

(defn do-need-work [^LocalDateTime time]
  "根据国家规定返回当前是否要工作的信息 2022
  Reference: http://www.gov.cn/zhengce/content/2020-11/25/content_5564127.htm
  Reference: http://www.gov.cn/zhengce/content/2021-10/25/content_5644835.htm"
  (let [time (if (nil? time) (LocalDateTime/now) time)
        of22 #(.atStartOfDay (LocalDate/of 2022 ^int %1 ^int %2))
        in (fn [time [hint & d]]
             (cond (= hint :each)
                   (some
                     #(.isEqual (.toLocalDate time)
                                (.toLocalDate (of22 (first %) (second %))))
                     (partition 2 (vec d)))
                   (= hint :range)
                   (and (.isAfter time (of22 (first d) (second d)))
                        (.isBefore time (.plusDays (of22 (nth d 2) (last d)) 1)))
                   (= hint :weekend)
                   (let [week (.getDayOfWeek time)]
                     (or (= DayOfWeek/SATURDAY week)
                         (= DayOfWeek/SUNDAY week)))
                   :else (throw (RuntimeException. "错误的匹配"))))]
    (cond
      (in time [:range 1 1, 1 3]) false
      (in time [:range 1 31, 2 6]) false
      (in time [:each 1 29, 1 30]) true
      (in time [:range 4 3, 4 5]) false
      (in time [:each 4 2]) true
      (in time [:range 4 30, 5 4]) false
      (in time [:each 4 24, 5 7]) true
      (in time [:range 6 3, 6 5]) false
      (in time [:range 9 10, 9 12]) false
      (in time [:range 10 1, 10 7]) false
      (in time [:each 10 8, 10 9]) true
      (in time [:weekend]) false
      :else true)))

(defn signin-hint [hcm-info]
  "根据 HCM 服务器返回的打卡信息 - [{}] 生成统计信息
  因为允许多次打卡，所以可能有 0 - n 条打卡信息"
  (let [hcm-info (sort-by :time hcm-info)
        need-work (do-need-work (LocalDateTime/now))
        ;;非工作日或工作日打了至少两次卡，最后一次在下午 3 点后
        off-work (or (not need-work)
                     (and (>= (count hcm-info) 2)
                          (>= (.getHour (:time (last hcm-info))) 15)))
        ;;工作日没有打过一次卡，则早上提示为 true
        morning-check (and need-work (empty? hcm-info))
        ;;工作时长计算：无数据返回 0，有数据则开始计算。
        ;;非工作日和工作日都从起点计算到终点，终点不足 17:30 的，按照当前时间计算（尚未下班）
        work-hour (if (empty? hcm-info)
                    0.0
                    (let [start (:time (first hcm-info))
                          end (:time (last hcm-info))
                          day (.toLocalDate start)
                          end (if (.isBefore end (.atTime day 17 30))
                                (.atTime day (LocalTime/now)) end)]
                      (Double/parseDouble
                        (format "%.2f"
                                (/ (.toMinutes (Duration/between start end))
                                   60.0)))))]
    (array-map
      :needWork need-work
      :offWork off-work
      :needMorningCheck morning-check
      :workHour work-hour)))

(defn handle-serve-day [{:keys [user secret adjust token] :as all}]
  "当日打卡服务，不兼容 go 版本 —— 如下所示，全大写，信息不全
  {
   'Srv_begin': 0,
   'Srv_end': 0,
   'Srv_all': 0,
   'Result': {
      'Data': {
               'Signin': null,
               'State': ''
               },
      'Error': '',
      'Success': false
      }
   }
   "
  (try
    (let [adjust (if-not adjust 0 adjust)
          info (get-hcm-info {:time (.plusDays (LocalDateTime/now) adjust) :token token})
          info-message (-> info :message)
          signin (signin-data info)]
      {:message info-message :data signin})
    (catch Exception e
      {:message (str "获取数据失败！" (.getMessage e))})))

(defn handle-serve-hint [{:keys [user secret token] :as all}]
  "当日提示服务 - 尽可能兼容 Go 版本"
  (try
    (let [adjust 0
          info (get-hcm-info {:time (.plusDays (LocalDateTime/now) adjust) :token token})
          info-data (-> info :data)
          signin (signin-data info)
          {:keys [needWork offWork needMorningCheck workHour]} (signin-hint signin)]
      (array-map
        :NeedWork needWork
        :OffWork offWork
        :NeedMorningCheck needMorningCheck
        :WorkHour workHour
        ;;兼容性保留，不过 Go 返回的原始信息为首字母大写版本
        :OriginData info-data
        :Date (.format (LocalDateTime/now)
                       DateTimeFormatter/ISO_LOCAL_DATE)
        ;;兼容性保留
        :Overtime {:Planned false
                   :Ordered false
                   :Checked false}
        :Breath (array-map
                  :UpdateTime "2022-03-07T08:48:02.804888789+08:00"
                  :TodayBreathMinutes 0
                  :DayCountNow 2
                  :LastUpdate "2022-03-07T08:48:02.804888789+08:00"
                  :MaxDayCount 13
                  :MaxDayLastUpdate "2022-03-07T08:48:02.804888789+08:00"
                  )
        :Blue (array-map
                :UpdateTime "2022-03-07T08:48:02.804888789+08:00"
                :IsTodayBlue false
                :WeekBlueCount 0
                :MonthBlueCount 1
                :MaxNoBlueDay 6
                :MaxNoBlueDayLastDay "2022-03-07T08:48:02.804888789+08:00")
        :FitnessEnergy
        (array-map
          :Fitness (array-map
                     :UpdateTime "2022-03-07T08:48:02.804888789+08:00"
                     :TodayCalories 0
                     :TodayRestingCalories 0
                     :IsOK false
                     :CountNow 94
                     :LastUpdate "2022-03-07T08:48:02.804888789+08:00"
                     :MaxCount 94
                     :MaxLastUpdate "2022-03-07T08:48:02.804888789+08:00"
                     :FitnessHint "0-0?")
          :Energy (array-map
                    :UpdateTime "2022-03-07T08:48:02.804888789+08:00"
                    :TodayEnergy 0
                    :WeenAvgEnergy 0
                    :MonthAvgEnergy 0)
          :TodayNetCalories 0
          :TodayCutCalories 0
          :AchievedCutGoal false)
        :Clean (array-map
                 :MorningBrushTeeth false
                 :NightBrushTeeth false
                 :MorningCleanFace false
                 :NightCleanFace false
                 :HabitCountUntilNow 0
                 :HabitHint "0-1?")))
    (catch Exception e
      {:message (str "获取数据失败！" (.getMessage e))})))

(defn handle-serve-today [{:keys [user secret token plainText] :as all}]
  "Google Pixel 服务，根据打卡信息返回一句话"
  (let [now (LocalDateTime/now)
        is-morning (< (.getHour now) 12)
        info (get-hcm-info {:time now :token token})
        signin (signin-data info)
        {:keys [needWork offWork needMorningCheck workHour]} (signin-hint signin)]
    (if needWork
      (cond (and is-morning (not needMorningCheck))
            {:status 1 :message "打上班卡成功。"}
            (and (not is-morning) offWork)
            {:status 1 :message "打下班卡成功。"}
            :else
            (do (log/info "[hcm-card-check] info: " info)
                {:status 0 :message "没有成功打卡。"}))
      {:status  1
       :message "今日无需工作。"})))

(comment
  (def data (get-hcm-info {:time (.minusDays (LocalDateTime/now) 1)}))
  (signin-data data))