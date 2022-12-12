(ns cyberme.cyber.slack
  "Slack 通知模块"
  (:require [clojure.string :as str]
            [org.httpkit.client :as client]
            [cheshire.core :as json]
            [cyberme.config :refer [edn-in]]
            [clojure.tools.logging :as l]))

(defn notify [message to]
  (try
    (let [url (if (str/includes? (str/upper-case to) "PIXEL")
                (edn-in [:slack :pixel]) (edn-in [:slack :server]))
          req (client/request {:url url
                               :method :post
                               :headers {"Content-Type" "application/json"}
                               :body (json/generate-string {:text message})})
          resp @req]
      (if (str/includes? (:body resp) "ok")
        {:message "消息发送成功" :status 1}
        (do (l/info "[slack] " resp)
            {:message "消息发送失败" :status 0})))
    (catch Exception e
      {:message (str "消息发送失败: " (.getMessage e)) :status 0})))

(defn serve-notice [{:keys [from channel message]
                     :or {from "NoBody" channel "Inspur"}}]
  "Slack 简单消息发送服务，For Pixel"
  (notify (format "From %s : %s" from message) channel))