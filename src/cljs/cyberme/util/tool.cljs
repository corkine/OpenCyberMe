(ns cyberme.util.tool
  (:require [clojure.string :as string]
            [cljs-time.core :as t]
            [cljs-time.format :as format]
            [cuerdas.core :as str]))

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