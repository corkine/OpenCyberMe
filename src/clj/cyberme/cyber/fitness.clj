(ns cyberme.cyber.fitness
  (:require [cyberme.db.core :as db]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:import (java.time LocalDate LocalDateTime Duration)
           (java.util Locale)
           (java.time.format DateTimeFormatter)))

(defn json->data [json-data return-map?]
  (let [data (json/parse-string json-data true)
        need-keys [:activeactivity
                   :distance
                   :restactivity
                   :floor
                   :heart
                   :restheart
                   :walkheart
                   :heartvariability]
        _ (Locale/setDefault (Locale/SIMPLIFIED_CHINESE))
        str->time #(try
                     (LocalDateTime/parse (str %) (DateTimeFormatter/ofPattern "yyyy年M月d日 ah:mm"))
                     (catch Exception e
                       (log/warn "can't parse " (str %) ", e: " (.getMessage e))
                       (LocalDateTime/now)))
        value->double #(try
                         (Double/parseDouble %)
                         (catch Exception e
                           (log/warn (str "error parse " % ", exception: " (.getMessage e)))
                           0.01))
        duration->seconds #(try
                             (let [line (str/split % #":")]
                               (cond (= (count line) 1) 0
                                     (= (count line) 2)
                                     (+ (Integer/parseInt (second line))
                                        (* 60 (Integer/parseInt (first line))))
                                     (= (count line) 3)
                                     (+ (Integer/parseInt (last line))
                                        (* 60 (Integer/parseInt (second line)))
                                        (* 60 60 (Integer/parseInt (first line))))
                                     :else (do (log/warn "not except value: " %)
                                               0)))
                             (catch Exception e
                               (log/warn "not except value: " % ", message: "
                                         (.getMessage e))))
        full-data (reduce #(let [{:keys [start value end unit duration]} (get data %2)
                                 lines (fn [data] (str/split-lines data))
                                 category %2]
                             (into %
                                   (mapv (fn [s v e u d]
                                           (if return-map?
                                             {:start    (str->time s)
                                              :value    (value->double v)
                                              :end      (str->time e)
                                              :unit     u
                                              :duration (duration->seconds d)
                                              :category category}
                                             ;category, value, unit, start, "end", duration, hash
                                             [(name category) (value->double v) u (str->time s)
                                              (str->time e) (duration->seconds d)
                                              (str (.hashCode (str category (str s))))]))
                                         (lines start) (lines value)
                                         (lines end) (lines unit) (lines duration)))) [] need-keys)]
    full-data))

(defn handle-upload [json-data]
  (try
    (let [data (json->data json-data false)
          line-in (count data)
          [{count :next.jdbc/update-count}] (db/insert-fitness-batch {:records data})]
      {:message (str "批量上传成功，共 " line-in " 条数据，插入结果：" count)
       :status  1})
    (catch Exception e
      {:message (str "批量上传失败：" (.getMessage e))
       :status  0})))

(defn handle-list [{:keys [category lastDays limit]
                    :or   {lastDays 10 limit 200}}]
  (try
    (if category
      (db/all-fitness-by-cat-after-limit
        {:limit    limit
         :day      (.minusDays (LocalDate/now) lastDays)
         :category category})
      (db/all-fitness-after-limit
        {:limit limit
         :day   (.minusDays (LocalDate/now) lastDays)}))
    (catch Exception e
      {:message (str "列表失败：" (.getMessage e))
       :status  0})))

(defn handle-delete [{:keys [id]}]
  (try
    (let [{count :next.jdbc/update-count} (db/delete-fitness {:id id})]
      {:message (str "删除 " id " 成功：变动：" count)
       :status  1})
    (catch Exception e
      {:message (str "删除 " id " 失败：" (.getMessage e))
       :status  0})))

(defn handle-details [{:keys [id]}]
  (try
    {:message (str "列出 " id " 成功")
     :data    (db/details-fitness {:id id})
     :status  1}
    (catch Exception e
      {:message (str "列出 " id " 失败：" (.getMessage e))
       :status  0})))

(comment
  (db/delete-fitness {:id 1})
  (db/details-fitness {:id 1})
  (db/all-fitness {:limit 100})
  (db/all-fitness-after {:day (.minusDays (LocalDate/now) 3)})
  (db/all-fitness-after-limit
    {:limit 100
     :day   (.minusDays (LocalDate/now) 3)})
  (db/insert-fitness-batch
    {:records [["test1" "seconds" (LocalDateTime/now)
                (LocalDateTime/now) 1000 "HASHTEST"]]})
  (user/create-migration "fitness"))
