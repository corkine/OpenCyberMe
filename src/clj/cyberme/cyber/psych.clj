(ns cyberme.cyber.psych
  (:require [cyberme.db.core :as db]
            [clojure.tools.logging :as log]
            [next.jdbc :as jdbc]
            [cyberme.cyber.inspur :as inspur]
            [cyberme.tool :as tool]
            [cuerdas.core :as str])
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

(defn ^String print-map
  "将 {keyword: string} map 转换为字符串，每个 Entry 使用 \t 隔开"
  ([m split-by]
   (print-map m false #{} split-by))
  ([m just-value? replace-key-set split-by]
   (if (or (nil? m) (empty? m))
     ""
     (let [sb (StringBuffer.)]
       (doseq [entity (vec m)]
         (when-not (contains? replace-key-set (first entity))
           (.append sb
                    (if just-value?
                      (format "%s" (second entity))
                      (format "%s: %s" (name (first entity)) (second entity))))
           (.append sb split-by)))
       (.toString sb)))))

(defn ^String print-vec
  "将 [] vec 转换为字符串，每个项目使用 \t 隔开"
  ([m split-by is-kw?]
   (if (or (nil? m) (empty? m))
     ""
     (let [sb (StringBuffer.)]
       (doseq [entity m]
         (.append sb (format "%s" (if is-kw? (name entity) entity)))
         (.append sb split-by))
       (.toString sb))))
  ([m split-by]
   (print-vec m split-by false)))

(defn psych-data->table
  "将数据库保存的数据转换为 SPSS 可用格式：每个量表一块，每块的行表示被试，每块的列表示量表题目"
  [data-map-vec]
  (let [sb (StringBuffer.)
        user-tab (StringBuffer.)
        knowledge-tab (StringBuffer.)
        questionare-tab (StringBuffer.)
        data-map-vec (vec data-map-vec)]
    (let [q-header (filterv #(str/includes? (str %) "问卷") (keys (first data-map-vec)))]
      (.append questionare-tab (print-vec q-header ", " true))
      (.append questionare-tab "\n"))
    (doseq [data-map data-map-vec]
      (let [k? (fn [search] (filterv #(str/includes? (str %) search)
                                     (vec (keys data-map))))
            mark-data (-> data-map :标记数据)
            user-data (-> data-map :被试收集)
            start-time (or (-> data-map :开始时间) 1664412480000)
            user-uuid (-> data-map :被试收集 :uuid)
            front-knowledge-keys (sort (k? "前测知识"))
            back-knowledge-keys (sort (k? "后测知识"))
            question-keys (sort (k? "问卷"))
            experiment-keys (sort (k? "q"))]
        ;被试信息表打印
        (.append user-tab user-uuid)
        (.append user-tab ", ")
        (.append user-tab (print-map mark-data " "))
        (.append user-tab (print-map (select-keys user-data [:age :name :grade :gender :school-id]) " "))
        (.append user-tab "\n")
        ;前测知识、问卷和后测知识打印
        (doseq [fk front-knowledge-keys]
          (.append knowledge-tab (print-map (get data-map fk) false #{:image} " "))
          (.append knowledge-tab ", "))
        (doseq [bk back-knowledge-keys]
          (.append knowledge-tab (print-map (get data-map bk) false #{:image} " "))
          (.append knowledge-tab ", "))
        (doseq [ek experiment-keys]
          (.append knowledge-tab (print-map
                                   (update-in (get data-map ek) [:record-time] #(- % start-time))
                                   " "))
          (.append knowledge-tab ", "))
        (.append knowledge-tab "\n")
        ;问卷打印
        (doseq [qk question-keys]
          (.append questionare-tab (print-map (get data-map qk) true #{} ", "))
          #_(.append questionare-tab ", "))
        (.append questionare-tab "\n")))
    (.append sb (.toString user-tab))
    (.append sb "\n\n")
    (.append sb (.toString knowledge-tab))
    (.append sb "\n\n")
    (.append sb (.toString questionare-tab))
    (.append sb "\n\n")
    (.toString sb)))

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

(defn recent-log-plain
  [input]
  (let [{:keys [message data status]} (recent-log input)]
    (if (= status 1)
      (psych-data->table data)
      message)))