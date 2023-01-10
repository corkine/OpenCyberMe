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
  (.parse (DateTimeFormatter/ofPattern ldt-patten) formatted-ldt))

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
                                  (if date (assoc tickets :date (ldt->str date)) ticket))
                                tickets)
        need-store-tickets (reduce (fn [col {id :id :as tk}]
                                     (if-not (get origin-map id) (conj col tk) col))
                                   [] formatted-tickets)]
    (if-not need-store-tickets
      (log/info "[ticket] no new ticket in db, skip saving...")
      (do (set-ticket {:tickets (conj origin need-store-tickets)})
          (log/info "[ticket] saving new tickets count: "
                    (count need-store-tickets) " to db done.")))))

(defn handle-fetch-today-tickets
  "获取当日的 Ticket，如果存在，则返回上述结构体列表"
  (let [origin (:tickets (fetch-ticket))
        today-str (.format (LocalDate/now) (DateTimeFormatter/ofPattern ldt-day-part-patten))
        today-ticket (filterv #(str/starts-with? (or (:date %)) today-str) origin)]
    {:message "获取成功"
     :data    (if (empty? today-ticket) [] today-ticket)
     :status  1}))