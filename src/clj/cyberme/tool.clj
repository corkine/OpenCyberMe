(ns cyberme.tool
  (:require [cyberme.oss :as oss]
            [mount.core :as mount]
            [clojure.tools.logging :as log]
            [cyberme.config :as config])
  (:import (java.time LocalDate)
           (java.time.format DateTimeFormatter)))

(defn all-week-day
  "获取本周所有的 LocalDate 实例"
  []
  (let [today (LocalDate/now)
        today-day-of-week (.getValue (.getDayOfWeek today))
        week-first (.minusDays today (- today-day-of-week 1))]
    (take 7 (iterate #(.plusDays % 1) week-first))))

(defn all-day
  "获取最近所有的 LocalDate 实例"
  [day]
  (let [today (LocalDate/now)
        start-day (.minusDays today (- day 1))]
    (take day (iterate #(.plusDays % 1) start-day))))

(defn today-str
  "获取今天的日期，2022-03-01 格式"
  []
  (.format (LocalDate/now)
           (DateTimeFormatter/ISO_LOCAL_DATE)))

(def bucket nil)

(mount/defstate ^:dynamic oss-client
                :start (let [{:keys [endpoint ak sk bucket-name]} (config/edn :oss)]
                         (log/info "[OSS] starting OSS Client with endpoint " endpoint)
                         (let [client (oss/mk-oss-client endpoint ak sk)
                               bucket-info (oss/get-bucket-info client bucket-name)]
                           (log/info "[OSS] bucket info: " bucket-info)
                           (alter-var-root #'bucket (constantly client))
                           client))
                :stop (do
                        (log/info "[OSS] stop OSS Client...")
                        (oss/shut-client oss-client)
                        (alter-var-root #'bucket (constantly nil))))

