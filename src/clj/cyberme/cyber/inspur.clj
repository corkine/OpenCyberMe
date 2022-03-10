(ns cyberme.cyber.inspur
  (:require [org.httpkit.client :as client]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [cyberme.db.core :as db]
            [taoensso.carmine :as car :refer (wcar)]
            [cyberme.config :refer [edn-in edn]]
            [cyberme.cyber.slack :as slack]
            [cyberme.cyber.todo :as todo])
  (:import (java.time LocalDateTime LocalDate DayOfWeek LocalTime Duration)
           (java.time.format DateTimeFormatter)))

(def date-time (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))

(defonce token-cache (atom {}))

(defn set-cache [token]
  (swap! token-cache merge {:token   token
                            :expired (.plusDays (LocalDateTime/now) 15)}))

(defn fetch-cache []
  (let [{:keys [token expired]} @token-cache
        now (LocalDateTime/now)
        expired (if (string? expired)
                  (LocalDateTime/parse expired (DateTimeFormatter/ISO_LOCAL_DATE_TIME))
                  expired)
        out? (or (str/blank? (str expired)) (.isBefore expired now))]
    {:token (if out? nil token)}))

(def normal-work-hour
  (let [start (LocalTime/of 8 30)
        end (LocalTime/of 17 30)]
    (/ (.toMinutes (Duration/between start end)) 60.0)))

(defonce cache (atom {}))

(declare signin-data)

(defn hcm-info-from-cache [^String date]
  "从缓存获取数据，先检查数据库，数据库存在即返回，反之检查内存，内存存在且不过期则返回，反之删除过期数据并返回空"
  (try
    (let [{data :hcm} (db/get-signin {:day date})
          with-msg-status {:data    data
                           :message "获取 HCM INFO 数据成功 (database)"
                           :status  1}]
      (if (nil? data)                                       ;如果数据库找不到，则使用内存缓存
        (let [l2-cache (get @cache (keyword date))
              expired-at (or (:expired l2-cache) (.plusYears (LocalDateTime/now) 10))]
          (if (and l2-cache (.isAfter expired-at (LocalDateTime/now)))
            (do
              (log/info "[signin-cache-l2] hint mem cache for " date)
              l2-cache)                                     ;如果内存缓存存在且没有超时，则返回缓存，否则清空过期缓存并返回空
            (do
              (log/info "[signin-cache-l2] no mem cache for " date " or expired")
              (swap! cache dissoc (keyword date))
              nil)))
        with-msg-status))
    (catch Exception e
      (log/info "[signin-cache] failed internal because：" (.getMessage e))
      nil)))

(defn set-hcm-cache [^String date, info]
  (let [signin (signin-data info)]
    (if (and (>= (count signin) 2)
             (>= (.getHour (:time (last signin))) 15))
      (do (try
            (db/set-signin {:day date :hcm (:data info)})
            (catch Exception e
              (log/info "[signin-cache] failed to save to db: " (.getMessage e))))
          (log/info "[signin-cache] set cache! " date))
      (do (log/info "[signin-cache] miss because info <= 2 and last before 15:00")
          (let [expired-time (.plusSeconds (LocalDateTime/now) (edn-in [:hcm :l2-cache-seconds]))]
            (log/info "[signin-cache-l2] set l2 temp cache for " date
                      ", expired after " expired-time)
            (swap! cache assoc (keyword date) (assoc info :expired expired-time)))))))

(defn get-hcm-info [{:keys [^LocalDateTime time ^String token]}]
  "根据 Token 和时间从 HCM 服务器解析获取签到数据，返回 {:data :message}"
  (let [time (if (nil? time) (-> (LocalDateTime/now) (.format DateTimeFormatter/ISO_LOCAL_DATE))
                             (.format time DateTimeFormatter/ISO_LOCAL_DATE))
        cache-res (hcm-info-from-cache time)]
    (if-not cache-res
      (try
        (log/info "[hcm-request] cache miss! " time)
        (let [token (if (str/blank? token)
                      (let [cache-token (:token (fetch-cache))
                            _ (when-not cache-token
                                (slack/notify "HCM Token 过期！" "SERVER")
                                (throw (RuntimeException. "HCM Token 过期且没有 Token 参数传入！")))]
                        cache-token) token)
              req (client/request {:url     (edn-in [:hcm :check-url])
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
      (do #_(log/info "[hcm-request] get from cache hint!")
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

(defn compute-work-hour [hcm-info]
  "计算工作时长，精确计算，用于自我统计"
  (let [hcm-info (sort-by :time hcm-info)]
    ;;工作时长计算：无数据返回 0，有数据则开始计算。
    ;;非工作日和工作日都从起点计算到终点，终点不足 17:30 的，按照当前时间计算（尚未下班）
    (if (empty? hcm-info)
      0.0
      (let [start (:time (first hcm-info))
            end (:time (last hcm-info))
            day (.toLocalDate start)
            end (if (.isBefore end (.atTime day 17 30))
                  (.atTime day (LocalTime/now)) end)
            ;如果 end < 11:30 的，则 - 0
            ;如果 end < 13:10 的，则 - 当前时间-11:30 的时间
            ;如果 end < 17:30 的，则 - 午休时间
            ;如果 end < 18:30 的，则 - 0
            ;如果 end > 18:30 的，则减去晚饭时间
            endLT (.toLocalTime end)
            minusMinutes
            (cond (.isBefore endLT (LocalTime/of 11 30))
                  0
                  (.isBefore endLT (LocalTime/of 13 10))
                  (.toMinutes (Duration/between (LocalTime/of 11 30) endLT))
                  (.isBefore endLT (LocalTime/of 17 30))
                  (.toMinutes (Duration/between (LocalTime/of 11 30)
                                                (LocalTime/of 13 10)))
                  (.isBefore endLT (LocalTime/of 18 30))
                  (.toMinutes (Duration/between (LocalTime/of 17 30) endLT))
                  :else
                  (.toMinutes (Duration/between (LocalTime/of 17 30)
                                                (LocalTime/of 18 30))))]
        (Double/parseDouble
          (format "%.1f"
                  (/ (- (.toMinutes (Duration/between start end))
                        minusMinutes)
                     60.0)))))))

(defn compute-work-hour-duration [hcm-info]
  "计算工作时长，从起点计算到终点，包括午休和下班时间，用于计算加班时间 - 从8：30-17：30 的时间"
  (let [hcm-info (sort-by :time hcm-info)]
    ;;工作时长计算：无数据返回 0，有数据则开始计算。
    ;;非工作日和工作日都从起点计算到终点，终点不足 17:30 的，按照当前时间计算（尚未下班）
    (if (empty? hcm-info)
      0.0
      (let [start (:time (first hcm-info))
            end (:time (last hcm-info))
            day (.toLocalDate start)
            end (if (.isBefore end (.atTime day 17 30))
                  (.atTime day (LocalTime/now)) end)]
        (Double/parseDouble
          (format "%.1f"
                  (/ (.toMinutes (Duration/between start end))
                     60.0)))))))

(defn signin-hint [hcm-info]
  "根据 HCM 服务器返回的打卡信息 - [{}] 生成统计信息
  因为允许多次打卡，所以可能有 0 - n 条打卡信息"
  (let [hcm-info (sort-by :time hcm-info)
        one-day (let [some-data (:time (first hcm-info))]
                  (if some-data
                    some-data
                    (do (log/info "[signin-hint] no hcm info find, use today to calc need-work")
                        (LocalDateTime/now))))
        need-work (do-need-work one-day)
        ;;非工作日或工作日打了至少两次卡，最后一次在下午 3 点后
        off-work (or (not need-work)
                     (and (>= (count hcm-info) 2)
                          (>= (.getHour (:time (last hcm-info))) 15)))
        ;;工作日没有打过一次卡，则早上提示为 true
        morning-check (and need-work (empty? hcm-info))
        ;;工作时长计算：无数据返回 0，有数据则开始计算。
        ;;非工作日和工作日都从起点计算到终点，终点不足 17:30 的，按照当前时间计算（尚未下班）
        work-hour (compute-work-hour hcm-info)]
    (array-map
      :needWork need-work
      :offWork off-work
      :needMorningCheck morning-check
      :workHour work-hour)))

(defn handle-serve-day [{:keys [user secret adjust token] :as all}]
  "当日打卡服务，不兼容 go 版本 —— 全大写，信息不全"
  (try
    (let [adjust (if-not adjust 0 adjust)
          info (get-hcm-info {:time (.plusDays (LocalDateTime/now) adjust) :token token})
          info-message (-> info :message)
          signin (signin-data info)]
      {:message info-message :data signin})
    (catch Exception e
      {:message (str "获取数据失败！" (.getMessage e))})))

(defn week-days [adjust to-today]
  "返回一周的日期，adjust 用于按照周数往前后调整，to-today 为日期不包括今天之后的"
  (let [adjust (if adjust adjust 0)
        real-now (LocalDate/now)
        now (.plusWeeks real-now adjust)
        week (.getValue (.getDayOfWeek now))
        start (.minusDays now (- week 1))                   ;周一
        list (mapv #(.plusDays start %) (range 0 7))
        before-now (filter #(not (.isAfter % real-now)) list)]
    (if to-today before-now list)))

(defn month-days [adjust to-today]
  "返回一个月的日期，adjust 用于按照月数往前调整，本月返回今天及以前的日期"
  (let [adjust (if adjust adjust 0)
        real-now (LocalDate/now)
        now (.plusMonths real-now adjust)
        month-start (LocalDate/of (.getYear now) (.getMonth now) 1)
        month-end (.minusDays (.plusMonths month-start 1) 1)
        list (mapv #(.plusDays month-start %) (range 0 (.getDayOfMonth month-end)))
        before-now (filter #(not (.isAfter % real-now)) list)]
    (if to-today before-now list)))

(defn fromYMD [^long y ^long m ^long d]
  (let [now (LocalDate/now)
        list (take-while #(not (.isAfter % now))
                         (iterate #(.plusDays % 1) (LocalDate/of y m d)))]
    list))

(defn handle-serve-this-week [{:keys [user secret adjust token] :as all}]
  "返回本周打卡记录，key 为日期，value 为数据，Go 版本兼容
  {:2022-03-07 {:srv_begin :result {:data {:signin}}"
  (let [before-now (week-days adjust true)]
    (if (empty? before-now)
      {}
      (apply assoc {}
             (flatten (mapv (fn [day]
                              (let [info (get-hcm-info {:time (.atStartOfDay day) :token token})]
                                [(keyword (.format day DateTimeFormatter/ISO_LOCAL_DATE))
                                 (:data info)])) before-now))))))

(defn overtime-hint [kpi token]
  "返回每月的加班信息"
  (let [kpi (if (nil? kpi) 70.0 (* kpi 1.0))
        now (LocalDate/now)
        rest-days (filter #(.isAfter % now) (month-days 0 false))
        rest-work-days (filter #(do-need-work (.atStartOfDay %)) rest-days)
        rest-work-days-count (+ (count rest-work-days) 1)   ;;今天加班不算，因此预测时要算
        passed-days (month-days 0 true)
        overtime-day-fn
        (fn [date]
          (let [info (get-hcm-info {:time (.atStartOfDay date) :token token})
                signin (signin-data info)
                workHour (compute-work-hour-duration signin)
                needWork (do-need-work (.atStartOfDay date))
                ;;工作日加班 - 正常时间，非工作日加班计算所有时间
                overHour (if needWork (- workHour normal-work-hour) workHour)
                ;;今日的不纳入加班计算，明日起开始计算
                overHour (if (.isEqual date (LocalDate/now)) 0 overHour)]
            #_(println "for " date "workHour " workHour " needWork " needWork " overHour " overHour)
            overHour))
        overtime-list (mapv overtime-day-fn passed-days)
        overtime-month-all (reduce + overtime-list)]
    {:MonthNeedKPI           kpi
     :WorkDayLeft            rest-work-days-count
     :OverTimePassed         (Double/parseDouble
                               (format "%.1f" overtime-month-all))
     :OverTimeAlsoNeed       (Double/parseDouble
                               (format "%.1f" (- kpi overtime-month-all)))
     :AvgDayNeedOvertimeWork (Double/parseDouble
                               (format "%.1f"
                                       (/ (* 1.0 (- kpi overtime-month-all)) rest-work-days-count)))}))

(defn overtime-hint-for-pre-month [kpi token]
  "返回每月的加班信息"
  (let [kpi (if (nil? kpi) 70.0 (* kpi 1.0))
        now (LocalDate/now)
        this-month-start (LocalDate/of (.getYear now) (.getMonth now) 1)
        month-start (.minusMonths this-month-start 1)
        month-days (month-days -1 false)
        overtime-day-fn
        (fn [date]
          (let [info (get-hcm-info {:time (.atStartOfDay date) :token token})
                signin (signin-data info)
                workHour (compute-work-hour-duration signin)
                needWork (do-need-work (.atStartOfDay date))
                ;;工作日加班 - 正常时间，非工作日加班计算所有时间
                overHour (if needWork (- workHour normal-work-hour) workHour)
                ;;今日的不纳入加班计算，明日起开始计算
                overHour (if (.isEqual date (LocalDate/now)) 0 overHour)]
            #_(println "for " date "workHour " workHour " needWork " needWork " overHour " overHour)
            overHour))
        overtime-list (mapv overtime-day-fn month-days)
        overtime-month-all (reduce + overtime-list)]
    {:MonthNeedKPI           kpi
     :WorkDayLeft            0
     :OverTimePassed         (Double/parseDouble (format "%.1f" overtime-month-all))
     :OverTimeAlsoNeed       (Double/parseDouble (format "%.1f" (- kpi overtime-month-all)))
     :AvgDayNeedOvertimeWork 0}))

(defn handle-serve-summary [{:keys [user secret kpi token
                                    todayFirst use2MonthData useAllData showDetails]
                             :or   {kpi           70
                                    todayFirst    true
                                    use2MonthData false
                                    useAllData    false
                                    showDetails   false}
                             :as   all}]
  "所有工作情况统计，Go API 兼容"
  (let [work-hour #(mapv (fn [day]
                           (let [info (get-hcm-info {:time (.atStartOfDay day) :token token})
                                 signin (signin-data info)]
                             (compute-work-hour signin))) %)
        raw-data #(mapv (fn [day]
                          (:data (get-hcm-info {:time (.atStartOfDay day) :token token}))) %)
        week-date (week-days 0 true)
        week-raw (raw-data week-date)
        week-work (work-hour week-date)
        week-work-hour (reduce + week-work)
        ;avg-work-hour-by-week (/ week-work-hour (count week-work))
        ;avg-week-work-hour-by-week (* 5 avg-work-hour-by-week)
        month-date (month-days 0 true)
        month-raw (raw-data month-date)
        month-work (work-hour month-date)
        month-work-hour (reduce + month-work)
        avg-work-hour-by-month (/ month-work-hour (count month-work))
        avg-week-work-hour-by-month (* 5 avg-work-hour-by-month)
        {:keys [avg-work-hour-by-month2
                avg-week-work-hour-by-month2
                month2-raw]}
        (if use2MonthData
          (let [month2-date (into (month-days 0 true) (reverse (month-days -1 true)))
                month2-raw (raw-data month2-date)
                month2-work (work-hour month2-date)
                month2-work-hour (reduce + month2-work)
                avg-work-hour-by-month2 (/ month2-work-hour (count month2-work))
                avg-week-work-hour-by-month2 (* 5 avg-work-hour-by-month)]
            {:avg-work-hour-by-month2      avg-work-hour-by-month2
             :avg-week-work-hour-by-month2 avg-week-work-hour-by-month2
             :month2-raw                   month2-raw})
          {:avg-work-hour-by-month2      nil
           :avg-week-work-hour-by-month2 nil
           :month2-raw                   []})
        {:keys [avg-work-hour-by-all
                avg-week-work-hour-by-all
                all-work-hour
                all-raw]}
        (if useAllData
          (let [all-date (fromYMD 2021 6 1)
                ;all-raw (raw-data all-date)
                all-work (work-hour all-date)
                all-work-hour (reduce + all-work)
                avg-work-hour-by-all (/ all-work-hour (count all-work))
                avg-week-work-hour-by-all (* 5 avg-work-hour-by-month)]
            {:avg-work-hour-by-all      avg-work-hour-by-all
             :avg-week-work-hour-by-all avg-week-work-hour-by-all
             :all-raw                   []
             :all-work-hour             all-work-hour})
          {:avg-work-hour-by-all      nil
           :avg-week-work-hour-by-all nil
           :all-raw                   []
           :all-work-hour             0.0})]
    (let [overtime-info (overtime-hint kpi token)
          overtime-last-info (overtime-hint-for-pre-month kpi token)]
      (array-map
        :Hint "HintWeekNeed 达到历史平均水平，本周还需工作时间
    HintPredWeekNeed 按照当前工作状态，相比较历史平均水平，本周会多/少工作时间
    OvertimeInfo：MonthNeedKPI 本周需要加班小时数，WorkDayLeft 本月剩余工作日，OverTimePassed 本月已经加班数，
    OverTimeAlsoNeed 本月剩余 KPI 加班小时数，AvgDayNeedOvertimeWork 本月剩余 KPI 平均每工作日加班小时数。
    其中 OvertimeInfoV2 不计算早上 8：30 之前的加班时长，LastMonthOvertimeInfo 统计的是上个月的加班时长。加班
    时长计算不请求 HCM 服务器，仅从缓存数据中推算，因此需要保证缓存数据包含了所有目标日期的数据。"
        :Note "使用最近一个月数据计算得出, ?showDetails=true 显示详情"
        :CurrentDate (.format (LocalDate/now) DateTimeFormatter/ISO_LOCAL_DATE)
        :WeekWorkHour (Double/parseDouble (format "%.1f" week-work-hour))
        :MonthWorkHour (Double/parseDouble (format "%.1f" month-work-hour))
        :AllWorkHour (Double/parseDouble (format "%.1f" all-work-hour))
        :AvgDayWorkHour (Double/parseDouble
                          (format "%.1f"
                                  (or avg-work-hour-by-all
                                      avg-work-hour-by-month2
                                      avg-work-hour-by-month)))
        :AvgWeekWorkHour (Double/parseDouble
                           (format "%.1f"
                                   (or avg-week-work-hour-by-all
                                       avg-week-work-hour-by-month2
                                       avg-week-work-hour-by-month)))
        :HintWeekNeed "⌛"
        :HintPredWeekNeed "⌛"
        :OvertimeInfo overtime-info
        :OvertimeInfoV2 overtime-info
        :LastMonthOvertimeInfo overtime-last-info
        :LastMonthOvertimeInfoV2 overtime-last-info
        :WeekRawData week-raw
        :MonthRawData (if showDetails month-raw nil)
        :Month2RawData (if showDetails month2-raw nil)
        :AllRawData (if showDetails all-raw nil)))))

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
                  :MaxDayLastUpdate "2022-03-07T08:48:02.804888789+08:00")

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

(defn handle-serve-hint-summary [{:keys [kpi token focus]}]
  (let [hint (handle-serve-hint {:token token})
        summary (handle-serve-summary {:useAllData true
                                       :kpi kpi :token token})
        todo (todo/handle-today {:focus focus
                                 :showCompleted false})]
    (assoc hint :Summary summary
                :Todo todo)))

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

(defn handle-serve-set-auto [{:keys [date start end]}]
  "新增 Pixel 打卡条件，day 格式为 20220202 格式，card1/2 格式为 10:30-11:40"
  (try
    (let [[_ y m d] (re-find #"(\d\d\d\d)(\d\d)(\d\d)" date)
          [_ c1f1 c1f2 c1e1 c1e2] (re-find #"(\d\d):(\d\d)-(\d\d):(\d\d)" start)
          [_ c2f1 c2f2 c2e1 c2e2] (re-find #"(\d\d):(\d\d)-(\d\d):(\d\d)" end)]
      (if (some nil? [y m d c1f1 c1f2 c1e1 c1e2 c2f1 c2f2 c2e1 c2e2])
        {:message "传入参数解析失败。" :status 0}
        (let [pi (fn [^String in] (Integer/parseInt in))
              day (LocalDate/of (Integer/parseInt y) (Integer/parseInt m) (Integer/parseInt d))
              c1f (LocalTime/of (pi c1f1) (pi c1f2))
              c1e (LocalTime/of (pi c1e1) (pi c1e2))
              c2f (LocalTime/of (pi c2f1) (pi c2f2))
              c2e (LocalTime/of (pi c2e1) (pi c2e2))
              ok-c1? (.isAfter c1e c1f)
              ok-c2? (.isAfter c2e c2f)]
          (if-not (and ok-c1? ok-c2?)
            {:message (str "传入的日期范围不合法。") :status 0}
            (let [res (db/set-auto {:start1 c1f :end1 c1e
                                    :start2 c2f :end2 c2e
                                    :day    day})]
              {:message (str "设置成功： " res) :status 1})))))
    (catch Exception e
      (log/info e)
      {:message (str "传入参数解析失败：" (.getMessage e)) :status 0})))

(defn handle-serve-delete-auto [{:keys [date]}]
  (try
    (let [[_ y m d] (re-find #"(\d\d\d\d)(\d\d)(\d\d)" date)
          day (LocalDate/of (Integer/parseInt y) (Integer/parseInt m) (Integer/parseInt d))]
      {:message (str "删除成功！" date)
       :data    (db/delete-auto {:day day})
       :status  1})
    (catch Exception e
      {:message (str "删除失败：" (.getMessage e)) :status 0})))

(defn handle-serve-list-auto [{:keys [day] :or {day 6}}]
  (try
    {:message "列出成功！"
     :data    (db/list-auto-recent {:day day})
     :status  1}
    (catch Exception e
      {:message (str "列出失败：" (.getMessage e)) :status 0})))

(defn handle-serve-auto [{:keys [user secret needCheckAt] :as all}]
  "For Pixel, 自动检查当前上班状态是否满足目标条件"
  (try
    (log/info "[hcm-auto] req by pixel for " needCheckAt)
    (let [needCheckAt (str/trim (str/replace (str/replace needCheckAt ": " ":") "：" ":"))
          [_ h m] (re-find #"(\d+):(\d+)" (or needCheckAt ""))
          needCheck (LocalTime/of (Integer/parseInt h) (Integer/parseInt m))
          ;_ (println "need check at: " needCheck)
          {:keys [r1start r1end r2start r2end info]} (db/get-today-auto {:day (LocalDate/now)})
          ;_ (println "db return: " r1start r1end r2start r2end)
          existR1? (not (or (nil? r1start) (nil? r1end)))
          existR2? (not (or (nil? r2start) (nil? r2end)))
          inR1? #(not (or (.isBefore % r1start) (.isAfter % r1end)))
          inR2? #(not (or (.isBefore % r2start) (.isAfter % r2end)))
          in-range (or (and existR1? (inR1? needCheck)) (and existR2? (inR2? needCheck)))]
      (if in-range "YES" "NO"))
    (catch Exception e
      (log/error "[hcm-auto] error: " (.getMessage e))
      (str "解析数据时出现异常：可能是传入的时间无法解析或者不存在数据库表。" (.getMessage e)))))

(defn handle-set-cache [{:keys [token]}]
  (set-cache token)
  {:message (str "成功写入 Token： " token)
   :status  1})

(comment
  (def server1-conn {:pool {} :spec
                     {:uri "redis://admin:???@ct.mazhangjing.com:6379/"}})
  (defmacro wcar* [& body] `(car/wcar server1-conn ~@body))
  (defn merge-from-redis []
    "Redis 数据迁移工具，开发时使用"
    (mapv (fn [today]
            (let [date-str (.format today DateTimeFormatter/ISO_LOCAL_DATE)
                  {:keys [Srv_begin
                          Srv_end
                          Srv_all
                          Result]} (json/parse-string
                                     (wcar* (car/get (str "info:user:corkine:date:" date-str))) true)
                  {:keys [Signin State]} (:Data Result)
                  error (:Error Result)
                  success (:Success Result)
                  remove-st-fn (fn [{:keys [Source Time]}]
                                 {:source Source :time Time})
                  final (array-map
                          :srv_begin Srv_begin
                          :srv_end Srv_end
                          :srv_all Srv_all
                          :result (array-map
                                    :data (array-map
                                            :signin (mapv remove-st-fn Signin)
                                            :state State)
                                    :error error
                                    :success success))
                  _ (db/set-signin {:day today :hcm final})]
              final))
          (take-while #(.isBefore % (LocalDate/of 2022 03 07))
                      (iterate #(.plusDays % 1) (LocalDate/of 2021 06 01)))))
  (def data (get-hcm-info {:time (.minusDays (LocalDateTime/now) 1)}))
  (signin-data data))