(ns cyberme.cyber.note
  (:require [cyberme.db.core :as db])
  (:import (java.time LocalDateTime Duration)))

(defn fetch-last-id-note []
  (try
    (let [{:keys [id from content info create_at] :as all} (db/note-last)
          live (:liveSeconds info)
          data {:Id          id
                :From        from
                :Content     content
                :LiveSeconds live
                :LastUpdate  create_at}
          expired? (if (or (nil? all)
                           (and (not (nil? live)) (int? live)
                                (< live
                                   (.getSeconds (Duration/between create_at
                                                                  (LocalDateTime/now))))))
                     true false)]
      (if expired? {:message (str "数据存在，但已经过期。")
                    :status  0}
                   data))
    (catch Exception e
      {:message (str "获取失败。" (.getMessage e))
       :status  0})))

(defn fetch-note [id]
  (try
    (let [{:keys [from content info create_at]} (db/note-by-id {:id id})
          live (:liveSeconds info)
          data {:Id          id
                :From        from
                :Content     content
                :LiveSeconds live
                :LastUpdate  create_at}
          expired? (if (and (not (nil? live)) (int? live)
                            (< live
                               (.getSeconds (Duration/between create_at
                                                              (LocalDateTime/now)))))
                     true false)]
      (if expired? {:message (str "数据存在，但已经过期。")
                    :status  0}
                   data))
    (catch Exception e
      {:message (str "获取失败。" (.getMessage e))
       :status  0})))

(defn handle-quick-add-note [content]
  (try
    (let [from "QUICK"
          id (+ (rand-int 10000) 50000)
          liveSeconds 600
          _ (db/insert-note {:id   id :from from :content content
                             :info {:liveSeconds liveSeconds}})]
      {:message (str "Set Note done: " id) :status 1})
    (catch Exception e
      {:message (str "Set Note failure: " (.getMessage e)) :status 0})))

(defn handle-fetch-note [{:keys [id justContent quick content]}]
  (if quick
    (handle-quick-add-note content)
    (let [data (cond (nil? id)
                     (fetch-last-id-note)
                     :else
                     (fetch-note id))]
      (if justContent (:Content data) data))))

(defn handle-add-note [{:keys [id from content liveSeconds]}]
  (try
    (let [id (if (nil? id) (+ (rand-int 10000) 50000) id)
          data {:id      id
                :from    from
                :content content
                :info    {:liveSeconds liveSeconds}}
          _ (db/insert-note data)]
      {:message (str "新建 " id " 成功")
       :status  1
       :data    data})
    (catch Exception e
      {:message (str "新建 " id " 失败： " (.getMessage e))
       :status  0})))

(comment
  (user/create-migration "note")
  (user/migrate)
  (cyberme.db.core/bind)
  (db/all-note)
  (db/insert-note {:id 1 :from "" :content "" :info {}})
  (db/note-last)
  (db/note-by-id {:id 2}))

