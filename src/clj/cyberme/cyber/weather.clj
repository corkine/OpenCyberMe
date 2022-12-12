(ns cyberme.cyber.weather
  "彩云天气模块"
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [cyberme.config :refer [edn-in]]
            [cyberme.cyber.slack :as slack]
            [org.httpkit.client :as client])
  (:import (java.time Duration LocalDate LocalDateTime LocalTime)))

;;;;;;;;;;;;;; CONFIG ;;;;;;;;;;;;
(defn remap-weather-info [in]
  (cond (str/includes? in "最近的降雨带")
        (let [[_ target] (re-find #"最近的降雨带在(.*?)外呢" (str (or in "")))]
          (str "降雨带：" target "外"))

        (str/includes? in "，放心出门吧")
        (str/replace in "，放心出门吧" "")

        (str/includes? in "在室内休息休息吧")
        (str/replace in "，在室内休息休息吧" "，室内活动最佳")

        (str/includes? in "。还在加班么？注意休息哦")
        (str/replace in "。还在加班么？注意休息哦" "")

        (str/includes? in "出门还是带把伞吧")
        "附近正在下雨，记得带伞"

        :else in))

(defn will-notice-warn? [in]
  ;不再通知下雨，iOS Widget 界面已经可以很好的提供及时天气信息
  ;(str/includes? in "正在下")
  false)

;;;;;;;;;;;;;; CACHE ;;;;;;;;;;;;

(defonce weather-cache (atom {}))

(defonce temp-cache (atom {}))

(defn- diff-temp
  "根据 cache 计算相比较昨天的温度
  cache 格式：{#object[java.time.LocalDate 0x723be7a0 \"2022-12-01\"]
              {:origin {:date \"2022-12-01T00:00+08:00\", :max 1.0, :min -1.0, :avg -0.19},
               :min -1.0,
               :max 1.0,
               :avg -0.19}"
  [cache one-line?]
  (if-not (nil? cache)
    (let [now (LocalDate/now)
          {ymin :min ymax :max yavg :avg :as y} (get cache (.minusDays now 1))
          {tmin :min tmax :max tavg :avg :as t} (get cache now)]
      (cond (and y t)
            (if one-line?
              (format "[↑%.0f%+.0f↓%.0f%+.0f]"
                      ymax (- tmax ymax)
                      ymin (- tmin ymin))
              {:high     tmax
               :low      tmin
               :diffHigh (- tmax ymax)
               :diffLow  (- tmin ymin)})
            t
            (if one-line?
              (format "[↑%.0f↓%.0f]" tmax tmin)
              {:high tmax
               :low  tmin})
            :else
            (if one-line?
              ""
              nil)))
    (if one-line?
      ""
      nil)))

(defn- diff-temp-tomorrow
  "根据 cache 计算相比较明天的温度
  cache 格式：{#object[java.time.LocalDate 0x723be7a0 \"2022-12-01\"]
              {:origin {:date \"2022-12-01T00:00+08:00\", :max 1.0, :min -1.0, :avg -0.19},
               :min -1.0,
               :max 1.0,
               :avg -0.19}"
  [cache]
  (if-not (nil? cache)
    (let [now (LocalDate/now)
          {mmin :min mmax :max mavg :avg :as m} (get cache (.plusDays now 1))
          {tmin :min tmax :max tavg :avg :as t} (get cache now)]
      (cond (and m t)
            {:high     tmax
             :low      tmin
             :diffHigh (- mmax tmax)
             :diffLow  (- mmin tmin)}
            t
            {:high tmax
             :low  tmin}
            :else
            nil))
    nil))

(defn set-weather-cache! [place weather]
  (swap! weather-cache dissoc place)
  (swap! weather-cache assoc place {:weather (remap-weather-info weather)
                                    :origin  weather
                                    :update  (LocalDateTime/now)}))

(defn unset-weather-cache! [place]
  (swap! weather-cache dissoc place))

(defn put-temp-cache [place response]
  ;temperature_08h_20h ;temperature_20h_32h
  (if-let [temp (-> response :result :daily :temperature_08h_20h)]
    (let [today-temp (first temp)
          tomorrow-temp (second temp)
          today (LocalDate/now)
          tomorrow (.plusDays today 1)]
      (swap! temp-cache assoc-in [place :temp today]
             {:origin today-temp
              :min    (:min today-temp)
              :max    (:max today-temp)
              :avg    (:avg today-temp)})
      (when tomorrow-temp
        (swap! temp-cache assoc-in [place :temp tomorrow]
               {:origin tomorrow-temp
                :min    (:min tomorrow-temp)
                :max    (:max tomorrow-temp)
                :avg    (:avg tomorrow-temp)})))))

(defn get-weather-cache
  "包括 weather-cache 和 temp-cache"
  [place]
  (let [{:keys [weather ^LocalDateTime update] :as origin}
        (get @weather-cache place nil)]
    (cond (and weather update)                              ;白天
          (let [cache (get-in @temp-cache [place :temp])
                temp-info (diff-temp cache false)
                temp-future-info (diff-temp-tomorrow cache)]
            (assoc origin :weather (str weather " +"
                                        (.toMinutes (Duration/between
                                                      update (LocalDateTime/now)))
                                        "m")
                          :temp temp-info
                          :tempFuture temp-future-info))
          :else                                             ;晚上
          {:tempFuture (diff-temp-tomorrow place)})))

;;;;;;;;;;;;;; INTERNAL API ;;;;;;;;;;;
(defn- url [token locale]
  (format "https://api.caiyunapp.com/v2.6/%s/%s/weather?alert=true&dailysteps=2&hourlysteps=24"
          token locale))

(defn- check-weather
  "检查天气信息，token 为彩云 API，place 为目标经纬度，for-day? 是否获取当日天气/每小时天气一句话信息"
  [token name place for-day?]
  (try
    (let [response (json/parse-string
                     (:body @(client/request {:url     (url token place)
                                              :method  :get
                                              :headers {"Content-Type" "application/json"}}))
                     true)]
      (if (= "ok" (-> response :status))
        (do
          (put-temp-cache name response)
          (if for-day?
            (-> response :result :hourly :description)
            (-> response :result :minutely :description)))
        (log/error "[weather-service] api failed with: " (dissoc response :result))))
    (catch Exception e
      (log/error "[weather-service] failed to request: " (.getMessage e)))))

(defn weather-routine-once
  "每天早 7:00 预告当日天气，每天 8:00 - 20:00 预告本小时降雨情况。
  程序每 5 分钟运行一次，如果在时间范围内，则通知，否则不通知"
  ([ignore-time-now?]
   (let [now (LocalTime/now)
         hour (.getHour now)
         minute (.getMinute now)]
     (if (or ignore-time-now?
             (and (<= 0 minute) (< minute 5))
             (and (<= 25 minute) (< minute 30)))
       (let [token (edn-in [:weather :token])
             check-list (edn-in [:weather :check])]
         (doseq [check check-list]
           (let [locale-map (edn-in [:weather :map check])
                 locale (:locale locale-map)
                 name (:name locale-map)]
             (cond #_(= hour 7)
                   #_(if-let [weather (check-weather token check locale true)]
                     (slack/notify (str name ": " weather) "SERVER"))
                   (and (> hour 6) (<= hour 23))
                   (if-let [weather (check-weather token check locale false)]
                     (do (set-weather-cache! check weather)
                         #_(slack/notify (str name ": " weather) "PIXEL")
                         #_(when (will-notice-warn? weather)
                           (slack/notify (str name ": " weather) "SERVER"))))
                   :else
                   (unset-weather-cache! check))))))))
  ([] (weather-routine-once false)))

(defn backend-weather-routine []
  (while true
    (try
      (let [sleep-sec (* 5 60)]
        (try
          (log/debug "[weather-service] starting checking with server...")
          (weather-routine-once)
          (log/debug "[weather-service] end checking with server, try to sleep sec: " sleep-sec)
          (catch Exception e
            (log/info "[weather-service] checking with server failed: " (.getMessage e))))
        (Thread/sleep (* 1000 sleep-sec)))
      (catch Exception e
        (log/info "[weather-service] weather-service routine failed: " (.getMessage e))))))

;;;;;;;;;;;;;;;; COMMENT ;;;;;;;;;;;;;;

;百度经纬度查询 http://api.map.baidu.com/lbsapi/getpoint/index.html
;:status "ok" / "failed" with :error
;:result :alert {:status "ok" :content []} 预警信息
;        :realtime {:status "ok" :pressure :air_quality :cloudrate :wind :temperature..} 实时天气
;        :minutely {:status "ok" :precipitation_2h [] :precipitation [] :probability []
;                   :description 此总结为本小时总结, 比如 最近的降雨带在西南50公里外呢
;        :hourly {:status "ok" :skycon [] 每小时图标 :temperature 每小时温度 :visibility 每小时可见度
;                 :precipitation 每小时降雨 :apparent_temperature 每小时实际温度 :humidity 怡人度
;                 :wind 风速和风向 :cloudrate 云量 :air_quality 空气质量 :pressure 气压 :dswrf ??
;                 :description 一句话总结，此总结为当天总结, 比如 未来24小时多云
(comment
  (check-weather (edn-in [:weather :token])
                 :na-tie (:locale (edn-in [:weather :map :na-tie]))
                 false)
  (set-weather-cache! :na-tie
                      (check-weather (edn-in [:weather :token])
                                     :na-tie (:locale (edn-in [:weather :map :na-tie]))
                                     false))
  (json/parse-string
    (:body @(client/request {:url     (url (edn-in [:weather :token])
                                           (edn-in [:weather :map :na-tie :locale]))
                             :method  :get
                             :headers {"Content-Type" "application/json"}}))
    true))
