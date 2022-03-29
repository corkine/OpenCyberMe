(ns cyberme.diary.util
  (:require [cljs-time.format :as format]
            [cyberme.util.tool :as tool]
            [cljs-time.core :as t]))

(defn diary-date
  "定位出一篇笔记的日期，首先判断 :info -> :day 2022-03-01 如果为空则
  试图从 create_at 字符串解析，返回 cljs date 实例"
  [{:keys [create_at info]}]
  (format/parse-local-date
    (format/formatter "yyyy-MM-dd")
    (let [day (:day info)
          alter (tool/datetime->date create_at)]
      (if day day alter))))

(defn diary-date-str
  [diary]
  (format/unparse-local-date
    (format/formatter "yyyy-MM-dd") (diary-date diary)))

(defn satisfy-date
  "返回 diary 日期是否在 range-str 中，range-str 不能为空"
  [range-str diary]
  (let [diary-date (diary-date diary)
        now (t/at-midnight (t/time-now))
        week-day (t/day-of-week now)
        month-num (t/month now)
        year-num (t/year now)
        start-date (if range-str
                     (condp = range-str
                       "week" (t/minus now (t/period :days (- week-day 1)))
                       "month" (t/local-date-time year-num month-num 1)
                       "month2" (t/minus (t/local-date-time year-num month-num 1)
                                         (t/period :months 1))
                       "year" (t/local-date-time year-num 1 1))
                     now)]
    (not (t/before? diary-date start-date))))