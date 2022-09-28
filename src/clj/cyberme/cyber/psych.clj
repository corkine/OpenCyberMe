(ns cyberme.cyber.psych
  (:require [cyberme.db.core :as db]
            [clojure.tools.logging :as log]
            [next.jdbc :as jdbc]
            [cyberme.cyber.inspur :as inspur]
            [cyberme.tool :as tool])
  (:import (java.time LocalDateTime)))

(defn add-log
  [data]
  (try
    (db/add-log {:api  "psych-exp-01"
                 :info data})
    {:message "上传成功，现在可以安全关闭此页面。"
     :data    nil
     :status  1}
    (catch Exception e
      (log/error "log req error: " (str e))
      {:message (str "上传失败，请联系主试人员：" e)
       :data    nil
       :status  0})))

(defn recent-log
  [{:keys [day exp-id]}]
  (let [now (LocalDateTime/now)]
    (try
      {:message "获取数据成功"
       :data    (let [origin (db/logs-between {:api   "psych-exp-01"
                                               :start (.minusDays now (or day 2))
                                               :end   now})]
                  (vals (into {}
                              (filterv (fn [[k v]]
                                         (and (not (nil? k))
                                              (not (nil? exp-id))
                                              (= exp-id (get-in v [:标记数据 :exp-id]))))
                                       (mapv (fn [row]
                                               [(get-in row [:info :被试收集 :uuid])
                                                (get row :info)]) origin)))))
       :status  1}
      (catch Exception e
        (log/error "log fetch error: " (str e))
        {:message (str "获取数据失败" e)
         :data    nil
         :status  0}))))