(ns cyberme.inspur.core
  (:require [org.httpkit.client :as client]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:import (java.time LocalDateTime)
           (java.time.format DateTimeFormatter)))

(def date-time (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))

(def data-url "https://wx56b5ebcd0c7759e2.hcmcloud.cn/api/attend.view.employee.day")

(def login-url "https://wx56b5ebcd0c7759e2.hcmcloud.cn/login?v=1624152319627&next=%2Fservice%23%2Fattend%2Fview&app_type=service")

(def example-token "2|1:0|10:1646563780|5:token|56:NTgyNmExMDY2YzA5NDBhMTQ0NzBmZWQyNTViMTA1MjU5M2UwYzA1Yw==|1cf208bbc7baf4be4f6a9a976c7566bc55a30e478e6fb2ae14df00e81475920f")

(defn get-hcm-info [{:keys [^LocalDateTime time ^String token]}]
  "根据 Token 和时间从 HCM 服务器解析获取签到数据，返回 {:data :message}"
  (try
    (let [token (if (str/blank? token) example-token token)
          time (if (nil? time) (-> (LocalDateTime/now) (.format DateTimeFormatter/ISO_LOCAL_DATE))
                               (.format time DateTimeFormatter/ISO_LOCAL_DATE))
          req (client/request {:url data-url
                               :method :post
                               :body (format "{\"employee_id\":\"\",\"date\":\"%s\"}" time)
                               :headers {"Cookie" (str "token=\"" token "\"")}})
          {:keys [status body] :as full-resp} @req
          _ (when (not= status 200)
              (do
                (log/info "[hcm-request] response: " full-resp)
                (throw (RuntimeException. "服务器未正确返回数据，可能是登录过期"))))]
      {:data (json/parse-string body true)
       :message "获取 HCM INFO 数据成功"})
    (catch Exception e
      {:message (str "get-hcm-info failed：" (.getMessage e))})))

(defn signin-data [hcm-info]
  "从 HTTP 返回数据中解析签到数据：
  [{:source 武汉光谷软件园E区,
    :time #object[java.time.LocalDateTime 2022-03-05T09:30:44]}]"
  (let [signin-vec (-> hcm-info :data :result :data :signin)
        pure-sign-vec (mapv (comp (fn [{time-str :time :as origin}]
                                    (assoc origin :time (LocalDateTime/parse time-str date-time)))
                                  #(select-keys % [:source :time])) signin-vec)
        _ (println pure-sign-vec)]
    pure-sign-vec))

(def data (get-hcm-info {:time (.minusDays (LocalDateTime/now) 1)}))
(signin-data data)