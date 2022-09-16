(ns cyberme.util.tool
  (:require [clojure.string :as string]
            [cljs-time.core :as t]
            [cljs-time.format :as format]
            [goog.crypt :as crypt]
            [goog.crypt.base64 :as b64]
            [goog.string :as gstring]
            [cuerdas.core :as str])
  (:import goog.crypt.Sha1))

(defn encode-sha-b64 [plain]
  (let [sha (Sha1.)]
    (.update sha plain)
    (b64/encodeByteArray (.digest sha))))

(defn pass-encode [plain expired-seconds]
  (let [expired (+ (.getTime (js/Date.)) (* expired-seconds 1000))]
    (b64/encodeString
      (str (encode-sha-b64 (gstring/format "%s::%d" plain expired))
           "::" expired))))

(defn l->s [vect]
  (string/join ", " (if (nil? vect) [] vect)))

(defn s->l [input]
  (let [input (or input "")
        input (string/replace input "，" ",")]
    (vec (filter (comp not string/blank?)
                 (map string/trim (string/split input ","))))))

(defn today-str []
  (format/unparse-local (format/formatter "yyyy-MM-dd")
                        (t/time-now)))

(defn is-after-x [x]
  (>= x (t/hour (t/time-now))))

(defn datetime->time
  "将 2022-03-29T08:11:46.23122 转换为 08:11"
  [local-date-str]
  (second (re-find #"\d+-\d+-\d+T(\d+:\d+):\d+" local-date-str)))

(defn week-? []
  "获取当天星期几的字符串"
  (let [now (t/day-of-week (t/time-now))]
    (case now
      1 "周一"
      2 "周二"
      3 "周三"
      4 "周四"
      5 "周五"
      6 "周六"
      7 "周日")))

(defn datetime->simple
  "将 2022-03-29T08:11:46.12312 转换为 2022-03-29 08:11"
  [local-date-str]
  (if (or (nil? local-date-str) (str/empty? local-date-str))
    "Unknown DateTime"
    (let [[_ date time] (re-find #"(\d+-\d+-\d+)T(\d+:\d+):\d+" local-date-str)]
      (str date " " time))))

(defn datetime->date
  "将 2022-03-29T08:11:46.12312 转换为 2022-03-29"
  [local-date-str]
  (if (or (nil? local-date-str) (str/empty? local-date-str))
    nil
    (second (re-find #"(\d+-\d+-\d+)T\d+:\d+:\d+" local-date-str))))