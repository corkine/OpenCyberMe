(ns cyberme.cyber.track
  (:require [cyberme.config :refer [edn]]
            [org.httpkit.client :as client]
            [cheshire.core :as json]
            [cyberme.db.core :as db]
            [clojure.tools.logging :as l]
            [clojure.string :as str])
  (:import (java.time LocalDateTime Instant)))

(defn save-track-to-db [by info]
  "记录上报数据到数据库"
  (try
    (db/set-track {:by by :info info})
    (catch Exception e
      (l/info "[track] error to save to db: " (.getMessage e)))))

(defn req-loc [ak la lo]
  @(client/request {:url (format
                           (str "https://api.map.baidu.com/reverse_geocoding/v3/"
                                "?ak=%s&output=json"
                                "&coordtype=wgs84ll"
                                "&location=%f,%f") ak la lo)}))

(defn req-report [ak service-id by la lo]
  @(client/request {:url "http://yingyan.baidu.com/api/v3/track/addpoint"
                    :method :post
                    :form-params
                    {"ak"               ak
                     "service_id"       service-id
                     "entity_name"      (str/replace by "@" "_")
                     "latitude"         la
                     "longitude"        lo
                     "loc_time"         (.getEpochSecond (Instant/now))
                     "coord_type_input" "wgs84"}}))

(defn handle-track [{:keys [by lo la al ve ho]}]
  "上报数据先根据百度地图获取地理位置，然后记录到数据库，并根据策略选择是否上报给百度"
  (let [{:keys [ak service-id allow-devices]} (edn :location)
        {:keys [status message body]} (req-loc ak la lo)]
    (if (not= status 200)
      {:status 0 :message message}
      (let [{:keys [_ result]} (json/parse-string body true)
            {:keys [formatted_address business]} result
            data {:by                 by
                  :longitude          lo
                  :latitude           la
                  :altitude           al
                  :verticalAccuracy   ve
                  :horizontalAccuracy ho
                  :note1              formatted_address
                  :note2              business}
            _ (save-track-to-db by data)]
        (if-not (some #(str/includes? % by) (or allow-devices []))
          (let [resp (req-report ak service-id by la lo)
                body-data (json/parse-string (:body resp) true)
                {:keys [_ message]} body-data]
            {:status  1 :message (str "上报 " by " " message)})
          {:status 1 :message (str "记录成功，但没有上报百度，因为策略不允许：" by)})))))

(comment
  (user/create-migration "track_loc")
  (user/migrate)
  (cyberme.db.core/bind)
  (db/all-track)
  (db/set-track {:by "CORKINE" :info {:HELLO "WORLD"}}))



