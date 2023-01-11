(ns cyberme.info.ticket
  "12306 火车票数据库和 API"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [cyberme.db.core :as db]
    [cyberme.tool :as tool])
  (:import (java.time LocalDate LocalDateTime)
           (java.time.format DateTimeFormatter)))

(def start-day (LocalDate/of 1996 3 4))
(def ldt-patten "yyyyMMdd_HH:mm")
(def ldt-day-part-patten "yyyyMMdd")

(defn- fetch-ticket
  "从数据库获取配置项"
  []
  (try
    (or (:info (db/someday {:day start-day})) {})
    (catch Exception e
      (log/error "[marvel] fetch failed. " (.getMessage e))
      {})))

(defn- set-ticket
  "设置数据库配置项"
  [info]
  (try
    (db/set-someday {:day start-day :info info})
    (catch Exception e
      (log/error "[marvel] insert marvel failed." (.getMessage e)))))

(defn- ldt->str [^LocalDateTime ldt]
  (.format (DateTimeFormatter/ofPattern ldt-patten) ldt))

(defn- str->ldt [^String formatted-ldt]
  (LocalDateTime/parse formatted-ldt (DateTimeFormatter/ofPattern ldt-patten)))

(defn handle-set-ticket
  "持久化 ticket，结构：
  {:id string?
   :orderNo string?
   :date localDateTime?
   :start :end
   :trainNo :siteNo
   :originData string?
   :fallbackData? string?}"
  [{:keys [id] :as new-ticket}]
  (let [origin (:tickets (fetch-ticket))
        ldt-date (:date new-ticket)
        new-ticket (assoc new-ticket :date (ldt->str ldt-date))
        exist-in-db? (some #(= id (:id %)) origin)]
    (if exist-in-db?
      (log/info "[ticket] exist ticket in db, skip saving...")
      (do (set-ticket {:tickets (conj origin new-ticket)})
          (log/info "[ticket] saving new ticket" (:orderNo new-ticket) " to db done.")))))

(defn handle-set-tickets
  "持久化 ticket，结构：
  {:id string?
   :orderNo string?
   :date localDateTime?
   :start :end
   :trainNo :siteNo
   :originData string?
   :fallbackData? string?}"
  [tickets]
  (let [origin (:tickets (fetch-ticket))
        origin-map (reduce (fn [agg tk] (assoc agg (:id tk) tk))
                           {} origin)
        formatted-tickets (mapv (fn [{date :date :as ticket}]
                                  (if date (assoc ticket :date (ldt->str date)) ticket))
                                tickets)
        need-store-tickets (reduce (fn [col {id :id :as tk}]
                                     (if-not (get origin-map id) (conj col tk) col))
                                   [] formatted-tickets)]
    (if-not need-store-tickets
      (log/info "[ticket] no new ticket in db, skip saving...")
      (do (set-ticket {:tickets (flatten (conj origin need-store-tickets))})
          (log/info "[ticket] saving new tickets count: "
                    (count need-store-tickets) " to db done.")))))

(comment
  (def origin (:tickets (fetch-ticket)))
  (def origin-map (reduce (fn [agg tk] (assoc agg (:id tk) tk))
                          {} (:tickets (fetch-ticket))))
  (def formatted-tickets (mapv (fn [{date :date :as ticket}]
                                 (if date (assoc ticket :date (ldt->str date)) ticket))
                               tks))
  (def need-store-tickets (reduce (fn [col {id :id :as tk}]
                                    (if-not (get origin-map id) (conj col tk) col)) []
                                  formatted-tickets)))

(defn tickets-ldt-sorted []
  (let [origin (:tickets (fetch-ticket))
        tks-with-ldt (mapv (fn [{date :date :as tk}]
                             (assoc tk :date (str->ldt date))) origin)
        sorted-tks-by-date (sort (fn [{d1 :date} {d2 :date}]
                                   (* -1 (.compareTo ^LocalDateTime d1 ^LocalDateTime d2))) tks-with-ldt)]
    sorted-tks-by-date))

(defn handle-fetch-today-tickets []
  "获取当日的 Ticket，如果存在，则返回上述结构体列表"
  (let [tks (tickets-ldt-sorted)
        today (LocalDate/now)
        today-tks (filter #(.isEqual today (.toLocalDate (:date %))) tks)]
    {:message "获取成功"
     :data    (vec today-tks)
     :status  1}))

(defn handle-fetch-recent-tickets [{:keys [limit]}]
  "获取最近的车票"
  {:message "获取成功"
   :data    (vec (take (or limit 10) (tickets-ldt-sorted)))
   :status  1})