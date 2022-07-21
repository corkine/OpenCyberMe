(ns cyberme.cyber.weather
  (:require [cyberme.db.core :as db]
            [org.httpkit.client :as client]
            [org.httpkit.sni-client :as sni-client]
            [hickory.core :as hi]
            [hickory.select :refer [select child first-child tag] :as s]
            [clojure.tools.logging :as log]
            [clojure.set :as set]
            [cyberme.cyber.slack :as slack]
            [cyberme.config :refer [edn-in]]
            [cheshire.core :as json])
  (:import (java.time LocalTime LocalDateTime)))

(defn url [token locale]
  (format "https://api.caiyunapp.com/v2.6/%s/%s/weather?alert=true&dailysteps=1&hourlysteps=24"
          token locale))

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
  (json/parse-string
    (:body @(client/request {:url     (url (edn-in [:weather :token]) (edn-in [:weather :map :na-tie :locale]))
                             :method  :get
                             :headers {"Content-Type" "application/json"}}))
    true))

(defn check-weather
  "检查天气信息，token 为彩云 API，place 为目标经纬度，for-day? 是否获取当日天气/每小时天气一句话信息"
  [token place for-day?]
  (try
    (let [response (json/parse-string
                     (:body @(client/request {:url     (url token place)
                                              :method  :get
                                              :headers {"Content-Type" "application/json"}}))
                     true)]
      (if (= "ok" (-> response :status))
        (if for-day?
          (-> response :result :hourly :description)
          (-> response :result :minutely :description))
        (log/error "[weather-service] api failed with: " (dissoc response :result))))
    (catch Exception e
      (log/error "[weather-service] failed to request: " (.getMessage e)))))

(defonce weather-cache (atom {}))

(defn set-weather-cache! [place weather]
  (swap! weather-cache dissoc place)
  (swap! weather-cache assoc place {:weather weather
                                    :update  (LocalDateTime/now)}))

(defn unset-weather-cache! [place]
  (swap! weather-cache dissoc place))

(defn get-weather-cache [place]
  (get @weather-cache place nil))

;初步设计：每天早 7:00 预告当日天气，每天 8:00 - 20:00 预告本小时降雨情况
;程序每 5 分钟运行一次，如果在时间范围内，则通知，否则不通知
;TODO 如果不降雨，下一小时话和上一小时一样，则不提示，如果降雨，每小时都提示
(defn weather-routine-once []
  (let [now (LocalTime/now)
        hour (.getHour now)]
    (if (-> now (.getMinute) (<= 5))
      (let [token (edn-in [:weather :token])
            check-list (edn-in [:weather :check])]
        (doseq [check check-list]
          (let [locale-map (edn-in [:weather :map check])
                locale (:locale locale-map)
                name (:name locale-map)]
            (cond (= hour 7)
                  (if-let [weather (check-weather token locale true)]
                    (slack/notify (str name ": " weather) "SERVER"))
                  (and (> hour 7) (<= hour 20))
                  (if-let [weather (check-weather token locale false)]
                    (do (set-weather-cache! name weather)
                        (slack/notify (str name ": " weather) "SERVER")))
                  :else
                  (unset-weather-cache! name))))))))

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
