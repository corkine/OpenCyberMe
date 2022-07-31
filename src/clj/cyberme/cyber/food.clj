(ns cyberme.cyber.food
  "饮食健康监测与行为矫正模块。
  主要原理：客户端随时记录每天饮食状况，每天给出 KPI 和完成情况，每天上传 API 和食物给 1 分，完成 KPI 给 3 分
  统计每天、每周和每月分值，相应的分数可以抵扣想吃但是比较'奢侈'的食物"
  (:require [cyberme.db.core :as db]
            [clojure.tools.logging :as log]
            [cyberme.cyber.fitness :as fitness])
  (:import (java.time LocalDate LocalTime LocalDateTime)))