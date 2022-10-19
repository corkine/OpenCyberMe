(ns cyberme.tool
  (:require [cyberme.oss :as oss]
            [mount.core :as mount]
            [clojure.tools.logging :as log]
            [cyberme.config :as config]
            [clojure.string :as str])
  (:import (java.time LocalDate)
           (java.time.format DateTimeFormatter)
           (java.util Base64)
           (java.security MessageDigest)))

(defn all-week-day
  "获取本周所有的 LocalDate 实例"
  []
  (let [today (LocalDate/now)
        today-day-of-week (.getValue (.getDayOfWeek today))
        week-first (.minusDays today (- today-day-of-week 1))]
    (take 7 (iterate #(.plusDays % 1) week-first))))

(defn each-monday-of
  "获取从某个日期的周一开始(包括)向前 n 个周一的 LocalDate 序列"
  ([start n]
   (let [today-day-of-week (.getValue (.getDayOfWeek start))
         week-first (.minusDays start (- today-day-of-week 1))]
     (take n (iterate #(.minusDays % 7) week-first))))
  ([n]
   (each-monday-of (LocalDate/now) n)))

(defn all-day
  "获取最近所有的 LocalDate 实例"
  [day]
  (let [today (LocalDate/now)
        start-day (.minusDays today (- day 1))]
    (take day (iterate #(.plusDays % 1) start-day))))

(defn encode-sha-b64
  "将明文进行 SHA1 和 Base64 加密"
  [^String plain]
  (let [sha (MessageDigest/getInstance "SHA1")]
    (.update sha (.getBytes plain))
    (.encodeToString (Base64/getEncoder)
                     (.digest sha))))

(defn pass-encode
  "将明文密码加密，格式为 BASE64(BASE64(SHA1(PASS::TIMESTAMP))::TIMESTAMP)"
  [plain expired-seconds]
  (let [expired (+ (System/currentTimeMillis) (* expired-seconds 1000))]
    (.encodeToString (Base64/getEncoder)
                     (.getBytes
                       (str (encode-sha-b64 (format "%s::%d" plain expired))
                            "::" expired)))))

(defn pass-right?
  "将客户端传输的密码和数据库保存的 SHA1 加密的密码进行对比，正确且在时间范围，返回 true
  客户端密码格式：BASE64(BASE64(SHA1(PASS::TIMESTAMP))::TIMESTAMP)"
  [^String basic-secret ^String pass-db]
  (cond (str/blank? pass-db) true
        (str/blank? basic-secret) false
        :else
        (try
          (let [plain-secret (String. (.decode (Base64/getDecoder) basic-secret))
                [_ sha-pass-exp expired-at] (re-find #"(.*?)::(\d+)" plain-secret)]
            (if (and sha-pass-exp expired-at
                     (>= (Long/parseLong expired-at) (System/currentTimeMillis)))
              (let [sha-db-pass-exp
                    (encode-sha-b64 (str pass-db """::" expired-at))]
                (= sha-pass-exp sha-db-pass-exp))
              false))
          (catch Exception e
            (log/error "[pass-check] failed." (.getMessage e))
            false))))

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

