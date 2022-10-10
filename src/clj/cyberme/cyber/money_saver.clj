(ns cyberme.cyber.money-saver
  (:require [cyberme.db.core :as db]
            [clojure.tools.logging :as log]
            [clojure.set :as set]
            [next.jdbc :as jdbc]
            [cyberme.cyber.slack :as slack]
            [cyberme.config :refer [edn-in]]
            [clojure.string :as str])
  (:import (clojure.lang PersistentHashMap)))

(defn resp->
  ([data ^String message]
   {:status 1 :data data :message message}))

(defn error->
  ([^String message]
   {:status -1 :data nil :message message})
  ([^String info ^Throwable throwable]
   (log/error throwable)
   {:status  -1 :data nil
    :message (str info ": " (.getMessage throwable))}))

(defn all-goals []
  (try
    (jdbc/with-transaction
      [t db/*db*]
      (let [all-goals (db/find-saver t)
            all-goals-with-logs
            (mapv (fn [goal]
                    (if-let [goal-id (:id goal)]
                      (let [logs (db/find-saver-logs t {:goal_id goal-id})]
                        (assoc goal :logs logs))
                      goal)) all-goals)]
        all-goals-with-logs))
    (catch Exception e
      (error-> "获取全部 Goals 失败" e))))

(defn create-goal [{:keys [name info]}]
  (try
    (resp-> (db/create-saver {:name name :info info}) "创建成功")
    (catch Exception e
      (error-> "创建 Goal 失败" e))))

(defn create-goal-log [goal-id info]
  (try
    (resp-> (db/create-saver-log {:goal_id goal-id :info info}) "创建成功")
    (catch Exception e
      (error-> "创建 Goal Log 失败" e))))

(defn drop-goal-log [log-id]
  (try
    (resp-> (db/drop-saver-log {:id log-id}) "删除 Log 失败")
    (catch Exception e
      (error-> "删除 Goal Log 失败" e))))

(defn drop-goal [goal-id]
  (try
    (resp-> (db/drop-saver {:id goal-id}) "删除 Goal 成功")
    (catch Exception e
      (error-> "删除 Goal 失败" e))))

(defn update-goal [id {:keys [name info]}]
  (try
    (resp-> (db/update-saver {:id id :info info :name name}) "修改 Goal 成功")
    (catch Exception e
      (error-> "修改 Goal 失败" e))))

(defn update-goal-log [id info]
  (try
    (resp-> (db/update-saver-log {:id id :info info}) "修改 Goal Log 成功")
    (catch Exception e
      (error-> "修改 Goal Log 失败" e))))

(comment
  (db/find-saver {:search ? :from ? :to ? :take ? :drop ?})
  (db/create-saver {:name ! :info !})
  (db/update-saver {:info ! :id !})
  (db/drop-saver {:id !})
  (db/find-saver-logs {:goal_id ! :from ? :to ? :take ? :drop ?})
  (db/create-saver-log {:goal_id ! :info !})
  (db/update-saver-log {:id ! :info !})
  (db/drop-saver-log {:id !}))