(ns cyberme.cyber.inspur
  (:require [org.httpkit.client :as client]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [cyberme.db.core :as db]
            [taoensso.carmine :as car :refer (wcar)]
            [cyberme.config :refer [edn-in edn]]
            [cyberme.cyber.slack :as slack]
            [cyberme.cyber.todo :as todo]
            [cyberme.cyber.clean :as clean]
            [cyberme.cyber.fitness :as fitness]
            [cyberme.cyber.express :as express]
            [cyberme.cyber.mini4k :as mini4k]
            [cyberme.tool :as tool]
            [cyberme.cyber.weather :as weather])
  (:import (java.time LocalDateTime LocalDate DayOfWeek LocalTime Duration)
           (java.time.format DateTimeFormatter)
           (java.util UUID)))

(def date-time (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))
(def c7-00 (LocalTime/of 7 0))
(def c8-40 (LocalTime/of 8 40))
(def c9-00 (LocalTime/of 9 0))
(def c10-00 (LocalTime/of 10 0))
(def c17-30 (LocalTime/of 17 30))
(def c20-20 (LocalTime/of 20 20))

(defonce token-cache (atom {}))
(defonce cache (atom {}))
(defonce visit-data (atom []))

(declare signin-data)

(defn d-format
  "格式化小数保留指定位数"
  ([d]
   (Double/parseDouble (format "%.1f" d)))
  ([d dot]
   (if (= dot 0)
     (Math/round ^double d)
     (Double/parseDouble (format (str "%." dot "f") d)))))

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

(defn hcm-info-from-cache
  "从缓存获取数据，先检查数据库，数据库存在即返回，反之检查内存，内存存在且不过期则返回，反之删除过期数据并返回空"
  [^String date]
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
              (log/debug "[signin-cache-l2] hint mem cache for " date)
              l2-cache)                                     ;如果内存缓存存在且没有超时，则返回缓存，否则清空过期缓存并返回空
            (do
              (log/info "[signin-cache-l2] no mem cache for " date " or expired")
              (swap! cache dissoc (keyword date))
              nil)))
        with-msg-status))
    (catch Exception e
      (log/info "[signin-cache] failed internal because：" (.getMessage e))
      nil)))

(defn set-hcm-cache
  "两级缓存机制，对于临时数据保存在内存缓存中，关闭服务器会持久化到文件，启用服务器会从文件加载。
  对于持久数据保存在数据库中。因为取数据总是先从数据库取，然后才是内存，如果取不到则请求 HCM 服务器，
  为避免在不强制使用缓存（比如 Pixel 的 /auto 接口会强制查询 HCM 服务器并更新缓存）的情况下在
  减少 HCM 服务器访问的前提下尽可能保持数据一致性，对于保存缓存到数据库还是内存要小心区分。根据实际
  的打卡规则，当打卡数据的最后时间是昨天（必定是持久化的），今天的数据永远不持久化，因为不确定何时
  下班并且是否在正常下班后晚上 23:59 再打一次卡的情况，此时仅使用内存缓存。"
  [^String date info]
  (let [input-date (try
                     (LocalDate/parse date (DateTimeFormatter/ISO_LOCAL_DATE))
                     (catch Exception e
                       (log/error "[set-hcm-cache] failed to know which day info is,
                        try to parse date param but failed: " (.getMessage e))
                       (LocalDate/now)))
        is-yesterday (.isBefore input-date (LocalDate/now))]
    (if is-yesterday
      (do (log/info "[signin-cache] date is before today, set cache to db!")
          (try
            (db/set-signin {:day date :hcm (:data info)})
            (catch Exception e
              (log/info "[signin-cache] failed to save to db: " (.getMessage e))))
          (log/info "[signin-cache] set cache! " date))
      (do (log/info "[signin-cache] date is today, just set l2-cache!")
          (let [expired-time (.plusSeconds (LocalDateTime/now) (edn-in [:hcm :l2-cache-seconds]))]
            (log/info "[signin-cache-l2] set l2 temp cache for " date
                      ", expired after " expired-time)
            (swap! cache assoc (keyword date) (assoc info :expired expired-time)))))))

(defn call-hcm
  "调用 HCM Http Request 获取服务器数据"
  [^String time, token]
  (swap! visit-data conj {:time (LocalDateTime/now) :for time :token token})
  @(client/request {:url     (edn-in [:hcm :check-url])
                    :method  :post
                    :body    (format "{\"employee_id\":\"\",\"date\":\"%s\"}" time)
                    :headers {"Cookie" (str "token=\"" token "\"")}}))

(defn notice-expired-async []
  (if (let [now (.getHour (LocalTime/now))] (and (>= now 0) (<= now 5)))
    (log/error "[HCM] HCM Token 过期，可能是系统正在维护")
    (future (slack/notify "HCM Token 过期！" "SERVER"))))

(defn get-hcm-info
  "根据 Token 和时间从 HCM 服务器解析获取签到数据，返回 {:data :message}"
  [{:keys [^LocalDateTime time ^String token notUseCache]
    :or   {notUseCache false}}]
  (let [time (if (nil? time) (-> (LocalDateTime/now) (.format DateTimeFormatter/ISO_LOCAL_DATE))
                             (.format time DateTimeFormatter/ISO_LOCAL_DATE))
        cache-res (if notUseCache nil (hcm-info-from-cache time))]
    (if-not cache-res
      (try
        (if notUseCache (log/info "[hcm-request] ignore cache! " time)
                        (log/info "[hcm-request] cache miss! " time))
        (let [token (if (str/blank? token)
                      (let [cache-token (:token (fetch-cache))
                            _ (when-not cache-token
                                (notice-expired-async)
                                (throw (RuntimeException. "HCM Token 过期且没有 Token 参数传入！")))]
                        cache-token) token)
              {:keys [status body] :as full-resp} (call-hcm time token)
              _ (when (not= status 200)
                  (do (log/info "[hcm-request] response: " full-resp)
                      (notice-expired-async)
                      (throw (RuntimeException. "服务器未正确返回数据，可能是登录过期"))))
              info {:data    (json/parse-string body true)
                    :message "获取 HCM INFO 数据成功"
                    :status  1}
              _ (do
                  (log/info "[hcm-request] cached data")
                  (set-hcm-cache time info))]
          info)
        (catch Exception e
          (log/error "[get-hcm-info] failed: " (.getMessage e))
          {:message (str "get-hcm-info failed：" (.getMessage e))
           :status  0}))
      (do #_(log/info "[hcm-request] get from cache hint!")
        (update cache-res :message #(str % " (from cache)"))))))

(defn signin-data
  "从 HTTP 返回数据中解析签到数据：
  [{:source 武汉江岸区解放大道,
    :time #object[java.time.LocalDateTime 2022-03-05T09:30:44]}]"
  [hcm-info]
  (let [signin-vec (-> hcm-info :data :result :data :signin)
        pure-sign-vec (mapv (comp (fn [{time-str :time :as origin}]
                                    (assoc origin :time (LocalDateTime/parse time-str date-time)))
                                  #(select-keys % [:source :time])) signin-vec)]
    pure-sign-vec))

(defn do-need-work
  "根据国家规定返回当前是否要工作的信息 2022
  Reference: http://www.gov.cn/zhengce/content/2020-11/25/content_5564127.htm
  Reference: http://www.gov.cn/zhengce/content/2021-10/25/content_5644835.htm"
  [^LocalDateTime time]
  (let [time (if (nil? time) (LocalDateTime/now) time)
        of22 #(.atStartOfDay (LocalDate/of 2022 ^int %1 ^int %2))
        in (fn [time [hint & d]]
             (cond (= hint :each)
                   (some
                     #(.isEqual (.toLocalDate time)
                                (.toLocalDate (of22 (first %) (second %))))
                     (partition 2 (vec d)))
                   (= hint :range)
                   (and (not (.isBefore time (of22 (first d) (second d))))
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

(defn lt-now [] (LocalTime/now))

(defn ldt-now [] (LocalDateTime/now))

(defn ld-now [] (LocalDate/now))

(defn compute-work-hour
  "计算工作时长，精确计算，用于自我统计"
  [hcm-info is-today-and-need-work]
  (let [time-now (lt-now)
        date-now (ld-now)
        hcm-info (sort-by :time hcm-info)
        datetime>17 #(.isAfter (.toLocalTime ^LocalDateTime %) (LocalTime/of 17 0))
        datetime<9-today! #(and (.isBefore (.toLocalTime ^LocalDateTime %) (LocalTime/of 9 0))
                                (.isEqual (.toLocalDate ^LocalDateTime %) date-now))
        time>12 #(.isAfter ^LocalTime % (LocalTime/of 12 0))
        time<8:30 #(.isBefore ^LocalTime % (LocalTime/of 8 30))]
    ;;工作时长计算：无数据返回 0，有数据则开始计算。
    ;;非工作日和工作日都从起点计算到终点，终点不足 17:30 的，按照当前时间计算（尚未下班）
    (if (and (empty? hcm-info)
             (or
               (not is-today-and-need-work)
               (and is-today-and-need-work (time<8:30 time-now))))
      0.0                                                   ;空数据且当日无需工作或空数据且未到时间
      (let [[start end] (cond (empty? hcm-info)
                              [(LocalTime/of 8 30) time-now] ;空数据且忘了打上班卡并还没下班
                              (= (count hcm-info) 1)
                              (let [^LocalDateTime dt (-> hcm-info first :time)
                                    dt-time (.toLocalTime dt)
                                    is-today? (.isEqual (.toLocalDate dt) date-now)]
                                (if (time>12 dt-time)
                                  [(LocalTime/of 8 30) dt-time] ;没打上班卡但打了一次下班卡
                                  [dt-time (if is-today? time-now (LocalTime/of 17 30))])) ;正常工作没下班 or 非今天忘记打下班卡
                              (datetime>17 (-> hcm-info first :time)) ;没打上班卡但打了多次下班卡
                              [(LocalTime/of 8 30) (.toLocalTime ^LocalDateTime (-> hcm-info last :time))]
                              (datetime<9-today! (-> hcm-info last :time)) ;今天打了多次上班卡，但没打下班卡
                              [(.toLocalTime ^LocalDateTime (-> hcm-info first :time)) time-now]
                              :else                         ;正常打了上下班卡, 上了下午的半天班
                              [(.toLocalTime ^LocalDateTime (-> hcm-info first :time))
                               (.toLocalTime ^LocalDateTime (-> hcm-info last :time))])
            ;如果 end < 11:30 的，则 - 0
            ;如果 end < 13:10 的，则 - 当前时间-11:30 的时间
            ;如果 before<11:30, end < 17:30 的，则 - 午休时间
            ;如果 before<11:30, end < 18:30 的，则 - 当前时间-17:30 的时间和午休时间
            ;如果 before<11:30, end > 18:30 的，则减去晚饭时间和午休时间
            ;上述三者如果 before>11:30 表示上午没上班，不减去午休时间
            noon-time (.toMinutes (Duration/between (LocalTime/of 11 30)
                                                    (LocalTime/of 13 10)))
            noon-time (if (.isAfter start (LocalTime/of 11 30)) 0 noon-time)
            diner-time (.toMinutes (Duration/between (LocalTime/of 17 30)
                                                     (LocalTime/of 18 30)))
            minusMinutes
            (cond (.isBefore end (LocalTime/of 11 30))
                  0
                  (.isBefore end (LocalTime/of 13 10))
                  (.toMinutes (Duration/between (LocalTime/of 11 30) end))
                  (.isBefore end (LocalTime/of 17 30))
                  noon-time
                  (.isBefore end (LocalTime/of 18 30))
                  (+ (.toMinutes (Duration/between (LocalTime/of 17 30) end))
                     noon-time)
                  :else
                  (+ noon-time diner-time))]
        (Double/parseDouble
          (format "%.1f"
                  (/ (- (.toMinutes (Duration/between start end))
                        minusMinutes)
                     60.0)))))))

(defn compute-work-hour-duration
  "计算工作时长，从起点计算到终点，包括午休和下班时间，用于计算加班时间 - 从8：30-17：30 的时间"
  [hcm-info]
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

(defn signin-hint
  "根据 HCM 服务器返回的打卡信息 - [{}] 生成统计信息
  因为允许多次打卡，所以可能有 0 - n 条打卡信息"
  [signin-list]
  (let [hcm-info (sort-by :time signin-list)
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
        work-hour (compute-work-hour hcm-info need-work)]
    (array-map
      :needWork need-work
      :offWork off-work
      :needMorningCheck morning-check
      :workHour work-hour)))

(defn handle-set-cache [{:keys [token]}]
  (set-cache token)
  {:message (str "成功写入 Token： " token)
   :status  1})

(defn handle-serve-day
  "当日打卡服务，不兼容 go 版本 —— 全大写，信息不全"
  [{:keys [user secret adjust token] :as all}]
  (try
    (let [adjust (if-not adjust 0 adjust)
          info (get-hcm-info {:time (.plusDays (LocalDateTime/now) adjust) :token token})
          info-message (-> info :message)
          signin (signin-data info)]
      {:message info-message :data signin})
    (catch Exception e
      {:message (str "获取数据失败！" (.getMessage e))})))

(defn week-days
  "返回一周的日期，adjust 用于按照周数往前后调整，to-today 为日期不包括今天之后的"
  [adjust to-today]
  (let [adjust (if adjust adjust 0)
        real-now (LocalDate/now)
        now (.plusWeeks real-now adjust)
        week (.getValue (.getDayOfWeek now))
        start (.minusDays now (- week 1))                   ;周一
        list (mapv #(.plusDays start %) (range 0 7))
        before-now (filter #(not (.isAfter % real-now)) list)]
    (if to-today before-now list)))

(defn month-days
  "返回一个月的日期，adjust 用于按照月数往前调整，本月返回今天及以前的日期"
  [adjust to-today]
  (let [adjust (if adjust adjust 0)
        real-now (LocalDate/now)
        now (.plusMonths real-now adjust)
        month-start (LocalDate/of (.getYear now) (.getMonth now) 1)
        month-end (.minusDays (.plusMonths month-start 1) 1)
        list (mapv #(.plusDays month-start %) (range 0 (.getDayOfMonth month-end)))
        before-now (filter #(not (.isAfter % real-now)) list)]
    (if to-today before-now list)))

(defn day-from
  "返回从一个日期开始到今天的所有日期，LocalDate list"
  [^LocalDate start]
  {:pre  [(.isBefore start (LocalDate/now))]
   :post [(seq? %) (->> % first (instance? LocalDate))]}
  (let [day-reader (iterate #(.plusDays % 1) start)
        today (LocalDate/now)]
    (take-while #(not (.isAfter % today)) day-reader)))

(defn month-rest-days
  "返回一个月剩下的日期，adjust 用于按照月数往前调整，不包括今天"
  [adjust]
  (let [adjust (if adjust adjust 0)
        real-now (LocalDate/now)
        now (.plusMonths real-now adjust)
        month-start (LocalDate/of (.getYear now) (.getMonth now) 1)
        month-end (.minusDays (.plusMonths month-start 1) 1)
        list (mapv #(.plusDays month-start %) (range 0 (.getDayOfMonth month-end)))
        after-now (filter #(.isAfter % real-now) list)]
    after-now))

(defn fromYMD [^long y ^long m ^long d]
  (let [now (LocalDate/now)
        list (take-while #(not (.isAfter % now))
                         (iterate #(.plusDays % 1) (LocalDate/of y m d)))]
    list))

(defn handle-serve-this-week
  "返回本周打卡记录，key 为日期，value 为数据，Go 版本兼容
  {:2022-03-07 {:srv_begin :result {:data {:signin}}"
  [{:keys [user secret adjust token] :as all}]
  (let [before-now (week-days adjust true)]
    (if (empty? before-now)
      {}
      (apply assoc {}
             (flatten (mapv (fn [day]
                              (let [info (get-hcm-info {:time (.atStartOfDay day) :token token})]
                                [(keyword (.format day DateTimeFormatter/ISO_LOCAL_DATE))
                                 (:data info)])) before-now))))))

(defn overtime-hint
  "返回每月的加班信息"
  [kpi token]
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
                               (format "%.1f" (double overtime-month-all)))
     :OverTimeAlsoNeed       (Double/parseDouble
                               (format "%.1f" (- kpi overtime-month-all)))
     :AvgDayNeedOvertimeWork (Double/parseDouble
                               (format "%.1f"
                                       (/ (* 1.0 (- kpi overtime-month-all)) rest-work-days-count)))}))

(defn overtime-hint-for-pre-month
  "返回每月的加班信息"
  [kpi token]
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

(defn handle-serve-summary
  "所有工作情况统计，Go API 兼容"
  [{:keys [user secret kpi token
           todayFirst use2MonthData useAllData showDetails]
    :or   {kpi           70
           todayFirst    true
           use2MonthData false
           useAllData    false
           showDetails   false}
    :as   all}]
  (let [work-hour #(mapv (fn [day]
                           (let [info (get-hcm-info {:time (.atStartOfDay day) :token token})
                                 signin (signin-data info)]
                             (compute-work-hour signin false))) %)
        day-count (fn [date-list] (count (filter #(do-need-work (.atStartOfDay %)) date-list)))
        non-zero #(if (= % 0) 1 %)
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
        avg-work-hour-by-month (/ month-work-hour (-> month-date day-count non-zero))
        avg-week-work-hour-by-month (* 5 avg-work-hour-by-month)
        {:keys [avg-work-hour-by-month2
                avg-week-work-hour-by-month2
                month2-raw]}
        (if use2MonthData
          (let [month2-date (into (month-days 0 true) (reverse (month-days -1 true)))
                month2-raw (raw-data month2-date)
                month2-work (work-hour month2-date)
                month2-work-hour (reduce + month2-work)
                avg-work-hour-by-month2 (/ month2-work-hour (-> month2-date day-count non-zero))
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
                avg-work-hour-by-all (/ all-work-hour (-> all-date day-count non-zero))
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

(defn local-date [] (LocalDate/now))

(defn local-date-time [] (LocalDateTime/now))

(defn local-time [] (LocalTime/now))

(defn policy-oneday
  "返回每天的策略：是否存在，阻塞的、失败的和成功的计数"
  [^LocalDate day]
  (let [info-check-status #(filter (fn [c] (= (:status c) %2)) (or (:check %1) []))
        {:keys [r1start r1end r2start r2end info]} (db/get-today-auto {:day day})
        {:keys [mark-night-failed mark-morning-failed]} info
        count-not-check-failed (+ (if mark-morning-failed 1 0)
                                  (if mark-night-failed 1 0))
        exist-count (count (filter (comp not nil?) [(and r1start r1end) (and r2start r2end)]))]
    {:exist        (not (and (nil? r1start) (nil? r1end)
                             (nil? r2start) (nil? r2end)))
     :pending      (count (info-check-status info "ready!"))
     :failed       (+ (count (info-check-status info "failed!"))
                      count-not-check-failed)
     :success      (count (info-check-status info "done!"))
     :policy-count exist-count}))

(defn handle-serve-sometime-summary
  "返回特定时间段内每天的工作时长、上下班时间、检查策略和是否是休息日等信息
  用于前端页面展示考勤日历。
  [:2022-03-01 {:work-hour 23.1 :check-start 8:30 :check-end 17:30
                :work-day true :policy true}]"
  [{:keys [user secret date-list with-last-month-all-day] :as all}]
  (try
    (let [date-list (or date-list (month-days 0 true))
          calc-info #(let [info (get-hcm-info {:time (.atStartOfDay %)})
                           signin (signin-data info)
                           signin (sort-by :time signin)
                           work-day? (do-need-work (.atStartOfDay %))
                           workHour (compute-work-hour signin (and work-day? (.isEqual % (LocalDate/now))))]
                       {:work-hour   workHour
                        :check-start (first signin)
                        :check-end   (last signin)
                        :work-day    work-day?
                        :policy      (policy-oneday %)})
          pass-data (if-not (empty? date-list)
                      (apply assoc {}
                             (flatten
                               (mapv (fn [date]
                                       [(keyword (.format date DateTimeFormatter/ISO_LOCAL_DATE))
                                        (calc-info date)])
                                     date-list)))
                      {})
          rest-list (if with-last-month-all-day (month-rest-days 0) [])
          rest-data (if-not (empty? rest-list)
                      (apply assoc {}
                             (flatten
                               (mapv (fn [date]
                                       [(keyword (.format date DateTimeFormatter/ISO_LOCAL_DATE))
                                        {:work-hour 0
                                         :work-day  (do-need-work (.atStartOfDay date))
                                         :policy    (policy-oneday date)}])
                                     rest-list)))
                      {})
          res (merge pass-data rest-data)]
      {:message "获取成功！"
       :status  1
       :data    res})
    (catch Exception e
      (log/error e)
      {:message (str "获取失败：" (.getMessage e))
       :status  0})))

(defn handle-serve-month-summary
  "返回本月每天的工作时长、上下班时间、检查策略和是否是休息日等信息
  用于前端页面展示考勤日历。
  [:2022-03-01 {:work-hour 23.1 :check-start 8:30 :check-end 17:30
                :work-day true :policy true}]"
  [{:keys [user secret] :as all}]
  (handle-serve-sometime-summary
    (merge all {:date-list               (month-days 0 true)
                :with-last-month-all-day true})))

(defn get-hcm-hint
  "当日提醒的 HCM 部分计算"
  [{:keys [user secret token] :as all}]
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
      :SignIn signin)))

(defn handle-serve-hint
  "当日提示服务 - 包括打卡、加班、策略以及健身锻炼、清洁等数据。"
  [{:keys [user secret token] :as all}]
  (try
    (let [hcm-data (get-hcm-hint all)
          {:keys [active rest]} (fitness/today-active)]
      (merge
        hcm-data
        (array-map
          :Date (.format (LocalDateTime/now) DateTimeFormatter/ISO_LOCAL_DATE)
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

          :Blue (clean/handle-blue-show)
          :FitnessEnergy
          (array-map
            :Fitness (array-map
                       :UpdateTime "2022-03-07T08:48:02.804888789+08:00"
                       :TodayCalories (d-format active 0)
                       :TodayRestingCalories (d-format rest 0)
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
          :Clean (clean/handle-clean-show {}))))
    (catch Exception e
      {:message (str "获取数据失败！" (.getMessage e))})))

(def start-day (LocalDate/of 1996 3 3))

(defn fetch-marvel
  "从数据库获取配置项"
  []
  (try
    (or (:info (db/someday {:day start-day})) {})
    (catch Exception e
      (log/error "[marvel] fetch failed. " (.getMessage e))
      {})))

(defn set-marvel
  "设置数据库配置项"
  [info]
  (try
    (db/set-someday {:day start-day :info info})
    (catch Exception e
      (log/error "[marvel] insert marvel failed." (.getMessage e)))))

(defn dashboard-set-marvel
  "handle-dashboard 数据重映射，如果获取到的 :clean :HabitCountUntilNow 存在且大于 marvel :clean-max
  或者 :blue :MaxNoBlueDay 存在且大于 marvel :blue-max，那么更新记录，反之则不更新。
  remap 后的 dashboard data 添加了 :clean :MarvelCount 和 :blue :MarvelCount 字段。"
  [data]
  (try
    (let [{:keys [blue-max clean-max] :as all-old-marvel} (fetch-marvel)
          blue-max (or blue-max 0)
          clean-max (or clean-max 0)
          blue (or (-> data :blue :MaxNoBlueDay) 0)
          clean (or (-> data :clean :HabitCountUntilNow) 0)
          blue-marvel? (> blue blue-max)
          clean-marvel? (> clean clean-max)
          _ (if (or blue-marvel? clean-marvel?)
              (log/info "[marvel-re-mapping] set new marvel: old b bm c cm is: "
                        blue blue-max clean clean-max))]
      (cond (and blue-marvel? clean-marvel?)
            (set-marvel (assoc all-old-marvel
                          :blue-max blue
                          :clean-max clean
                          :blue-update (local-date-time)
                          :clean-update (local-date-time)))
            blue-marvel?
            (set-marvel (assoc all-old-marvel
                          :blue-max blue
                          :blue-update (local-date-time)))
            clean-marvel?
            (set-marvel (assoc all-old-marvel
                          :clean-max clean
                          :clean-update (local-date-time)))
            :else :no-marvel-set)
      (-> data
          (assoc-in [:blue :MarvelCount] (max blue-max blue))
          (assoc-in [:clean :MarvelCount] (max clean-max clean))))
    (catch Exception e
      (log/error "[marvel-re-mapping] compare and set marvel failed: " (.getMessage e))
      data)))

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
  :score {:2022-03-01
           {:blue true
            :fitness {:rest 2000 :active 300 :diet 300}
            :todo {:total 27 :finished 27}
            :clean {:m1xx :m2xx :n1xx :n2xx}
            :today 99}}"
  [{:keys [day] :or {day 7}}]
  (try
    (let [all-week-day (mapv (comp keyword str) (tool/all-week-day))
          today (keyword (tool/today-str))
          ;每一个子项都是 {:2022-03-01 xxx}
          ;要合并为 {:2022-03-01 {:blue xxx}}
          blue-week (clean/handle-blue-week)
          score-week (clean/handle-score-week)
          clean-week (clean/handle-clean-week)
          fitness-week (fitness/week-active)
          todo-week (todo/handle-week-static)
          ; 返回的所有数据
          data {:blue    (clean/handle-blue-show)
                :fitness (fitness/today-active)
                :clean   (clean/handle-clean-show {})
                :todo    (todo/handle-recent {:day day})
                :express (express/recent-express)
                :movie   (mini4k/recent-update {:day day})
                :work    (assoc (get-hcm-hint {})
                           :Policy (policy-oneday (local-date)))
                :today   (get score-week today)
                :score   (reduce #(assoc % (keyword %2)
                                           {:blue    (get blue-week %2)
                                            :today   (get score-week %2)
                                            :clean   (get clean-week %2)
                                            :fitness (get fitness-week %2)
                                            :todo    (get todo-week %2 [])})
                                 {} all-week-day)}]
      {:message "获取数据成功！" :status 1 :data (dashboard-set-marvel data)})
    (catch Exception e
      {:message (str "获取大屏信息失败！" (.getMessage e)) :status 0})))

(defn handle-serve-hint-summary-with-debug [{:keys [kpi token focus]}]
  (let [hint (time (let [res (handle-serve-hint {:token token})]
                     (println "for hint do timing: ")
                     res))
        summary (time (let [res (handle-serve-summary {:useAllData true
                                                       :kpi        kpi :token token})]
                        (println "for summary do timing: ")
                        res))
        summary (dissoc summary :Hint :Note :CurrentDate :WeekRawData)
        todo (time (let [res (todo/handle-today {:focus focus :showCompleted false})]
                     (println "for todo do timing: ")
                     res))]
    (assoc hint :Summary summary
                :Todo todo)))

(defn handle-serve-hint-summary [{:keys [kpi token focus id]}]
  (let [hint (handle-serve-hint {:token token})
        summary (handle-serve-summary {:useAllData true :kpi kpi :token token})
        todo (todo/handle-today {:focus focus :showCompleted false})
        w (weather/get-weather-cache (or (keyword id) :na-tie))]
    (assoc hint :Summary (dissoc summary :Hint :Note :CurrentDate :WeekRawData)
                :Todo todo
                :Weather w)))

(defn handle-serve-today
  "Google Pixel 服务，根据打卡信息返回一句话"
  [{:keys [user secret token useCache]
    :or   {useCache false} :as all}]
  (let [now (LocalDateTime/now)
        is-morning (< (.getHour now) 12)
        info (get-hcm-info {:time now :token token :notUseCache (not useCache)})
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

(defn handle-serve-set-auto
  "新增 Pixel 打卡条件，day 格式为 20220202 格式，card1/2 格式为 10:30-11:40"
  [{:keys [date start end]}]
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

(defn ^String handle-serve-auto
  "For Pixel, 自动检查当前上班状态是否满足目标条件，如果满足，则将此次查询记录在数据库中，以备
  如果其检查失败后，后台服务发送通知消息。
  传入的格式可以为 HH:mm 或者 h:m 或者 hh:m 或者 h:mm，: 可以为中文全角或半角，其前后可包含空格。
  返回值默认为 YES 或 NO，如果无法解析则返回一句话。
  如果 mustInRange 为 true，则返回 YES 还需要当前时间在策略范围内。
  如果检查的时间点和当前时间点均位于目标范畴，则更新数据库，否者不进行数据库操作。"
  [{:keys [user secret ^String needCheckAt mustInRange]
    :or   {mustInRange true}}]
  (try
    (log/info "[hcm-auto] req by pixel for " needCheckAt " with mustInRange? " mustInRange)
    (let [clock-now (local-time)
          needCheckAt (str/trim (str/replace (str/replace needCheckAt ": " ":") "：" ":"))
          [_ h m] (re-find #"(\d+):(\d+)" (or needCheckAt ""))
          needCheck (LocalTime/of (Integer/parseInt h) (Integer/parseInt m))
          {:keys [r1start r1end r2start r2end info]} (db/get-today-auto {:day (local-date)})
          existR1? (not (or (nil? r1start) (nil? r1end)))
          existR2? (not (or (nil? r2start) (nil? r2end)))
          inR1? #(not (or (.isBefore % r1start) (.isAfter % r1end)))
          inR2? #(not (or (.isBefore % r2start) (.isAfter % r2end)))
          in-range (or (and existR1? (inR1? needCheck)) (and existR2? (inR2? needCheck)))
          now-in-range (or (and existR1? (inR1? clock-now)) (and existR2? (inR2? clock-now)))]
      (when (and in-range now-in-range)
        ;必须检查的时间点和当前时间点都在范围内才算，否者在任何时候请求正确检查时间点接口
        ;都将添加 check 记录，那么后台服务一运行就会发现很多失败。
        (let [new-info (assoc (or info {}) :check
                                           (-> info :check
                                               (conj {:id     (str (UUID/randomUUID))
                                                      :start  (local-date-time)
                                                      :status "ready!"
                                                      :cost   600})))
              _ (db/update-auto-info {:day (local-date) :info new-info})]
          (log/info "[hcm-auto] update auto checking today: " new-info)))
      (if mustInRange
        (if (and now-in-range in-range) "YES" "NO")
        (if in-range "YES" "NO")))
    (catch Exception e
      (log/error "[hcm-auto] error: " (.getMessage e))
      (str "解析数据时出现异常：可能是传入的时间无法解析或者不存在数据库表。" (.getMessage e)))))

(defn auto-today-info-check!
  "检查数据库所有的 auto 的 info 的 check 字段，获取所有的检查项，如果检查项为 ready! 并且到期
  那么进行 HCM 检查并更新这些检查项信息，如果这些 check 项任一检查失败，那么异步通知 Slack。
  这里不必须使用 HCM 请求，因为一旦 AUTO 成功会不使用缓存请求 HCM 并缓存最新数据，因此检查只需要
  使用缓存数据即可。
  此外，如果存在策略，但是现在时间超过了所有策略时间且存在某个策略范围没有 check，将标记 today 完全失败，并发送通知。
  上午和下午分别计算自己时间段并通知自己的部分。"
  []
  (let [clock (local-time)
        {:keys [r1start r1end r2start r2end info]} (db/get-today-auto {:day (local-date)})
        {:keys [check mark-night-failed mark-morning-failed]} info
        ;四个条件表明策略失效：完备的策略、当前时间晚于最末策略结束时间、存在策略在检查中找不到、没有处理过数据
        failed-with #(and (not %3)
                          (not (or (nil? %1) (nil? %2)))
                          (and (.isAfter clock %1)
                               (.isAfter clock %2))
                          (not (some (fn [{start :start}]
                                       (if (nil? start)
                                         false
                                         (try
                                           (and (.isAfter (.toLocalTime (LocalDateTime/parse start)) %1)
                                                (.isBefore (.toLocalTime (LocalDateTime/parse start)) %2))
                                           (catch Exception e
                                             (log/error "[hcm-auto-routine] parse " start " error: " (.getMessage e))
                                             false)))) check)))
        now (local-date-time)
        afternoon? (> (.getHour now) 12)
        need-check (filterv (fn [{:keys [start cost status] :as all}]
                              "任何状态为 ready! 的，并且超过了其执行期限的"
                              (try
                                (and (= status "ready!")
                                     (.isBefore (.plusSeconds (LocalDateTime/parse start)
                                                              cost) now))
                                (catch Exception e
                                  (log/info "[hcm-auto-check] failed parse db data: " all
                                            "exception: " (.getMessage e))
                                  false))) (or check []))
        ;_ (log/info "[hcm-auto-check] need check: " need-check)
        nothing-check? (= (count need-check) 0)]
    ;如果有检查的话，则进行检查：
    (if-not nothing-check?
      (let [check-fn (fn [{:keys [id cost start]}]
                       ;获取当前 HCM 信息，如果是下午，应该 offWork，上午则不是 needMorningCheck
                       ;否者都算执行失败。
                       (let [data (get-hcm-info {:time now})
                             signin (signin-data data)
                             {:keys [needMorningCheck offWork]} (signin-hint signin)
                             good? (if-not afternoon? (not needMorningCheck) offWork)]
                         (when-not good?
                           (log/warn "[hcm-auto-check] not done for " id " start at "
                                     start " will end at seconds " cost))
                         {:id id :good? good?}))
            check-result (mapv check-fn need-check)
            _ (log/info "[hcm-auto-check] check result: " check-result)
            ;;将 [{:id :good?}] 转换为 {id {:good?}}
            check-result-map (reduce (fn [acc {:keys [id good?]}]
                                       (assoc acc id {:good? good?}))
                                     {} check-result)
            failed? (some #(not (:good? %)) check-result)
            ;;更新检查过状态的 check 信息，如果成功，标记为 done 失败标记为 failed
            updated-check (mapv (fn [{:keys [id] :as all}]
                                  (let [in-map-data (get check-result-map id)
                                        is-good? (:good? in-map-data)]
                                    (if in-map-data
                                      (assoc all :status (if is-good? "done!" "failed!"))
                                      all)))
                                (or check []))]
        (when failed?
          ;所有失败，仅异步通知一次
          (log/info "[hcm-auto-check] failed with task in list: " check-result)
          (future (slack/notify "检查 AUTO 失败，可能需要手动操作。" "SERVER")))
        (log/info "[hcm-auto-check] saving database with: " updated-check)
        (db/update-auto-info {:day (local-date) :info (assoc info :check updated-check)}))
      ;如果没有检查的话，如果策略过期，则通知并更新数据库
      (if afternoon?
        (when (failed-with r2start r2end mark-night-failed)
          (log/info "[hcm-auto-check] strategy no check with night!")
          (future (slack/notify "记录了策略，但是晚上没有任何检查发生！" "SERVER"))
          (db/update-auto-info {:day (local-date) :info
                                (assoc info :mark-night-failed true)}))
        (when (failed-with r1start r1end mark-morning-failed)
          (log/info "[hcm-auto-check] strategy no check with morning!")
          (future (slack/notify "记录了策略，但是早上没有任何检查发生！" "SERVER"))
          (db/update-auto-info {:day (local-date) :info
                                (assoc info :mark-morning-failed true)}))))))

(defn backend-hcm-auto-check-service
  "仅在白天的 7:00 - 8:40 以及下午的 17:30 - 20:20 进行检查，检查间隔为 1 分钟一次"
  []
  (while true
    (try
      (let [sleep-sec (or (edn-in [:hcm :auto-check-seconds]) 60)
            now (LocalTime/now)
            is-morning? (and (.isAfter now c7-00) (.isBefore now c8-40))
            is-later-morning? (and (.isAfter now c9-00) (.isBefore now c10-00))
            is-night? (and (.isAfter now c17-30) (.isBefore now c20-20))]
        (when (or is-morning? is-later-morning? is-night?)
          (try
            #_(log/info "[hcm-auto-check-routine] starting checking database auto...")
            (auto-today-info-check!)
            #_(log/info "[hcm-auto-check-routine] end checking, try to sleep sec: " sleep-sec)
            (catch Exception e
              (log/error "[hcm-auto-check-routine] failed: " (.getMessage e)))))
        (Thread/sleep (* 1000 sleep-sec)))
      (catch Exception e
        (log/error "[hcm-auto-check-routine] routine failed: " (.getMessage e))))))

(comment
  (def server1-conn {:pool {} :spec
                     {:uri "redis://10.69.65.87:6379/"}})
  (defmacro wcar* [& body] `(car/wcar server1-conn ~@body))
  (doseq [t (range 10)]
    (time (do (doseq [i (range 1000)]
                (wcar* (time (car/set (str "testkey-" i) "networkId")))))))

  (time (do (doseq [i (range 1000)]
              (wcar* (time (car/set (str "testkey-" i) "networkId"))))))

  (doseq [t (range 10)]
    (time (wcar* (doseq [i (range 1000)]
                   (car/set (str "testkey-" i) "networkId"))))))

(comment
  (def server1-conn {:pool {} :spec
                     {:uri "redis://10.69.65.87:6379/"}})
  (defmacro wcar* [& body] `(car/wcar server1-conn ~@body))
  (in-ns 'cyberme.db.core)
  (conman/bind-connection *db* "sql/queries.sql" "sql/goods.sql" "sql/cyber.sql")
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
  (db/get-today-auto {:day (LocalDate/now)})
  (db/update-auto-info {:day (LocalDate/now) :info
                        {:check [{:id     "0332d9c6-823f-4d7d-966e-8e9710b7e30c",
                                  :cost   600,
                                  :start  (LocalDateTime/now),
                                  :status "ready!"}]}})
  (LocalDateTime/parse "2022-03-15T16:00:39.931")
  (signin-data data))