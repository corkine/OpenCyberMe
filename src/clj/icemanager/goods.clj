(ns icemanager.goods
  (:require
    [icemanager.db.core :as db]
    [clojure.string :as string]
    [next.jdbc :as jdbc])
  (:import (java.time LocalDateTime)
           (java.time.format DateTimeFormatter)))

(def formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss.SSS"))

(defn time->str [^LocalDateTime time]
  (if (nil? time) nil (.format time formatter)))

(defn all-place-with-goods []
  "将 SQL 返回的行列结果进行按位置聚合，将数据库字段转换为大小写驼峰样式，全部返回"
  (try
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
      {:data    (vec map-data)
       :status  1
       :message "获取所有位置和物品成功。"})
    (catch Exception e
      {:data    nil
       :status  0
       :message (str "获取所有位置和物品失败：" (.getMessage e))})))

(defn add-place [data]                                      ;place,location,description
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

(defn edit-place [data]                                     ;id place location description
  "更新位置信息"
  (try
    (db/edit-place data)
    {:status  1
     :message "更新成功"
     :data    nil}
    (catch Exception e
      {:status  0
       :message (str "更新失败：" (.getMessage e))
       :data    nil})))

(defn delete-place [data]                                   ;id
  "删除位置，其中项目全部移动到 #1 中去"
  (try
    (jdbc/with-transaction
      [t db/*db*]
      (when (= (:id data) "1") (throw (RuntimeException. "不能删除 #1 位置")))
      (let [_ (db/reset-goods-placeId t data)
            _ (db/delete-place t data)]
        {:status  1
         :message (str "删除位置成功。")
         :data    nil}))
    (catch Exception e
      {:status  0
       :message (str "删除失败：" (.getMessage e))
       :data    nil})))

(defn add-package [{:keys [name description]}]              ;name,description -> name,info->description
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

(defn get-packages [{:keys [day] :or {day 3}}]
  "获取最近的打包"
  (try
    {:status  1
     :message (str "获取打包成功。")
     :data    (db/get-packages {:day day})}
    (catch Exception e
      {:status  0
       :message (str "获取最近 " day " 天的打包失败：" (.getMessage e))
       :data    nil})))

(defn add-good [{:keys [uid name placeId] :as data}]
  ;uid,name,label,status,placeId,note* -> uid,name,info{label,status,note},placeId
  "新建项目"
  (try
    (let [uid (if (nil? uid) uid (string/upper-case uid))
          full-data {:uid     uid
                     :name    name
                     :info    (dissoc data :uid :name :placeId)
                     :placeId placeId}]
      (db/add-good full-data)
      {:status  1
       :message (str "物品入库成功：" full-data)
       :data    nil})
    (catch Exception e
      {:status  0
       :message (str "物品入库失败：" (.getMessage e))
       :data    nil})))

(defn hide-good [id-map]                                    ;id:string
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

(defn delete-good [id-map]                                  ;id:string
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

(defn box-good [{:keys [id box-id is-plan]}]                ;id:string,box-id:string
  "确定打包项目"
  (try
    (jdbc/with-transaction
      [t db/*db*]
      (let [{:keys [name]} (db/get-package-info t {:id box-id})
            _ (when (string/blank? name)
                (throw (RuntimeException.
                         (str "数据库获取打包 #" box-id "返回空名称"))))
            {old-package :packages} (db/get-good-packages t {:id id})
            old-package
            (if (nil? old-package)
              [] (filter #(not= (str (:id %)) (str box-id)) old-package))
            all-package (conj old-package {:id     (Integer/parseInt box-id)
                                           :status (if is-plan 0 1)
                                           :name   name})
            _ (db/update-good-packages {:id       id
                                        :packages (vec all-package)})]
        {:status  1
         :message (format "打包 %s 到包裹 %s 成功" id name)
         :data    nil}))
    (catch Exception e
      {:status  0
       :message (str "打包失败：" (.getMessage e))
       :data    nil})))

(defn unbox-good [{:keys [id box-id]}]                      ;id:string,box-id:string
  "取消打包项目"
  (try
    (jdbc/with-transaction
      [t db/*db*]
      (let [{:keys [name]} (db/get-package-info t {:id box-id})
            _ (when (string/blank? name)
                (throw (RuntimeException.
                         (str "数据库获取打包 #" box-id "返回空名称"))))
            {old-package :packages} (db/get-good-packages t {:id id})
            all-package
            (if (nil? old-package)
              [] (filter #(not= (str (:id %)) (str box-id)) old-package))
            _ (db/update-good-packages {:id       id
                                        :packages (vec all-package)})]
        {:status  1
         :message (format "从包裹 %s 取消打包物品 #%s 成功" name id)
         :data    nil}))
    (catch Exception e
      {:status  0
       :message (str "取消打包失败：" (.getMessage e))
       :data    nil})))

