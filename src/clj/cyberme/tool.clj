(ns cyberme.tool
  (:import (java.time LocalDate)))

(defn all-week-day
  "获取本周所有的 LocalDate 实例"
  []
  (let [today (LocalDate/now)
        today-day-of-week (.getValue (.getDayOfWeek today))
        week-first (.minusDays today (- today-day-of-week 1))]
    (take 7 (iterate #(.plusDays % 1) week-first))))