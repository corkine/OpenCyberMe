(ns icemanager.goods
  (:require
    [icemanager.db.core :as db])
  (:import (java.time LocalDateTime)
           (java.time.format DateTimeFormatter)))

(def formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss.SSS"))

(defn time->str [^LocalDateTime time]
  (if (nil? time) nil (.format time formatter)))

(defn all-place-with-goods []
  "将 SQL 返回的行列结果进行按位置聚合，将数据库字段转换为大小写驼峰样式，全部返回"
  (let [data (db/all-place)
        grouped-data (group-by :placeid data)
        map-data
        (map #(let [place-goods (val %)
                    {:keys [placeid place location description placeupdateat]} (first place-goods)
                    no-place-goods
                    (map
                      (fn [good]
                        (let [fix-camel (assoc good
                                          :createAt (time->str (:createat good))
                                          :updateAt (time->str (:updateat good)))
                              no-place (dissoc fix-camel
                                               :placeid :place
                                               :location :description
                                               :createat :updateat
                                               :placeupdateat)] no-place))
                      place-goods)
                    filtered-goods
                    (filter (fn [g] (not (nil? (:id g)))) no-place-goods)]
                {:id          placeid
                 :place       place
                 :location    location
                 :description description
                 :updateAt    (time->str placeupdateat)
                 :items       filtered-goods})
             grouped-data)]
    {:data (vec map-data)}))

(defn add-place [data] ;place,location,description
  "添加新位置"
  (try
    (db/add-place data)
    {:status  1
     :message "新建成功"
     :data    nil}
    (catch Exception e
      {:status  0
       :message (str "新建失败：" (.getMessage e))
       :data    nil})))

(defn add-package [{:keys [name description]}] ;name,description -> name,info->description
  "添加新打包"
  (try
    (db/add-package {:name name :info {:description description}})
    {:status  1
     :message "新建成功"
     :data    nil}
    (catch Exception e
      {:status  0
       :message (str "新建失败：" (.getMessage e))
       :data    nil})))

(defn hide-good [id-map] ;id:string
  "隐藏项目"
  (try
    (db/hide-good id-map)
    {:status  1
     :message (str "隐藏 " (:id id-map) " 成功")
     :data    nil}
    (catch Exception e
      {:status  0
       :message (str "隐藏失败：" (.getMessage e))
       :data    nil})))

(defn delete-good [id-map] ;id:string
  "删除项目"
  (try
    (db/delete-good id-map)
    {:status  1
     :message (str "删除 " (:id id-map) " 成功")
     :data    nil}
    (catch Exception e
      {:status  0
       :message (str "删除失败：" (.getMessage e))
       :data    nil})))


