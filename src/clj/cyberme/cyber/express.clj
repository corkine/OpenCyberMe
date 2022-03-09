(ns cyberme.cyber.express
  (:require [org.httpkit.client :as client]
            [cheshire.core :as json]
            [cyberme.db.core :as db]
            [clojure.tools.logging :as l]
            [cyberme.cyber.slack :as slack]
            [clojure.string :as str])
  (:import (java.time LocalDateTime)))

(def kuai-di100 "https://kuaidi100.market.alicloudapi.com/getExpress?NO=%s&TYPE=%s")

(def app-code "bc5cf43ec23e40ccbf7424ec8774252c")

(defn simple-check [{:keys [no kind code]
                     :or   {kind "AUTO" code app-code}}]
  (try
    (let [req (client/request {:url     (format kuai-di100 no kind)
                               :method  :get
                               :headers {"Authorization" (str "APPCODE " code)}})
          resp @req]
          ;_ (clojure.pprint/pprint resp)

      {:message "检查成功。"
       :data    (json/parse-string (:body resp) true)
       :status  1})
    (catch Exception e
      {:message (str "检查失败，方法未返回预期 JSON 对象。" (.getMessage e))
       :status  0})))

(defn handle-track [{:keys [no] :as all} note kind]
  (try
    (let [_ (db/set-express-track {:no no :track all
                                   :info {:status 1 :note note :kind kind}})]
      {:message "追踪设置成功。" :status 1})
    (catch Exception e
      {:message (str "追踪设置失败。 " (.getMessage e)) :status 0})))

(defn simple-track [{:keys [no kind note code]
                     :or   {kind "AUTO" code app-code}}]
  "快递追踪，先查找数据库，找到即返回（不更新数据库数据），找不到则查询 API，并将结果保存到数据库：
  如果当前状态为运输中，则标记为运输，如果当前状态为已完毕或未找到则保存到数据库并标记为已完毕。"
  (try
    (let [{:keys [track] :as exist} (db/find-express {:no no})]
      (if exist
        {:message (str "追踪的快递已经存在: " no)
         :data    track
         :status  0}
        (let [req (client/request {:url     (format kuai-di100 no kind)
                                   :method  :get
                                   :headers {"Authorization" (str "APPCODE " code)}})
              resp @req
              ;_ (clojure.pprint/pprint resp)
              {:keys [status state] :as all} (json/parse-string (:body resp) true)
              need-track? (not (or (not= status 200) (= state 3) (= state 4)))]
          (if need-track?
            (handle-track all note kind)
            (do
              ;;为了避免下次查询浪费 API 资源
              (db/set-express-track {:no no :track all
                                     :info {:status 0 :note note :kind kind}})
              {:message (str "无需检查此快递，可能是单号错误或者状态错误/已完成。")
               :data    all
               :status  0})))))
    (catch Exception e
      {:message (str "追踪失败，方法未返回预期 JSON 对象。" (.getMessage e))
       :status  0})))

(defn track-routine []
  "每间隔一段时间执行一次的数据库数据查询"
  (try
    (doseq [{:keys [no info track]} (db/need-track-express)]
      (l/info (str "[express-track] checking express id: " no ", info: " info))
      (let [{:keys [kind note]} info
            kind (if (str/blank? kind) "AUTO" kind)
            old-list (get track :list [])
            {:keys [message status data]}
            (simple-check {:no no :kind kind})]
            ;_ (println "data: " data)

        (if (= status 0)
          (l/warn (str "[express-track] failed for check " no ", msg: " message))
          (do
            ;;这里要检查是否结束、是否有新数据需要推送
            (let [{:keys [status state list]} data
                  need-track? (not (or (not= status 200) (= state 3) (= state 4)))
                  track-id (if need-track? 1 0)
                  new-data? (> (count list) (count old-list))
                  {:keys [time content]} (first list)]
              (when new-data?
                (let [msg (str "您的快递 [" (or note no) "] 有更新：" time " " content)]
                  (l/info (str "[express-track] new state: " msg))
                  (slack/notify msg "EXPRESS")))
              (l/info "[express-track] update express for " (or note no) " set status: " track-id)
              (db/update-express {:no no :track data
                                  :info {:note note :kind kind :status track-id}}))))))
    (catch Exception e
      (l/info "[express-track] exception: " (.getMessage e)))))

(defn backend-express-service []
  (while true
    (try
      (let [sleep-sec (* 60 30)]
        (try
          (l/info "[express-service] starting sync with server...")
          (let [now (LocalDateTime/now)
                hour (.getHour now)
                is-night? (and (> hour 23) (< hour 7))]
            (if-not is-night?
              (track-routine)
              (l/info "[express-service] skip night check express...")))
          (l/info "[express-service] end sync with server, try to sleep sec: " sleep-sec)
          (catch Exception e
            (l/info "[express-service] sync with ms-server failed: " (.getMessage e))))
        (Thread/sleep (* 1000 sleep-sec)))
      (catch Exception e
        (l/info "[express-service] express-service routine failed: " (.getMessage e))))))

(comment
  (count nil)
  (db/all-express)
  (db/need-track-express)
  (db/find-express {:no "JD0067098256241000"})
  (db/set-express-track {:no 1 :track {:a "B"} :info {:status 1}})
  (db/update-express {:no "1" :track {} :info {:status 0}})
  (db/delete-express {:no "JD0067098256241000"})
  (simple-check {:no "123"})
  (in-ns 'user)
  (start)
  (in-ns 'user)
  (migrate)
  (create-migration "express")
  (in-ns 'cyberme.cyber.express)
  (in-ns 'cyberme.db.core)
  (bind))