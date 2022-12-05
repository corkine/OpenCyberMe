(ns cyberme.media.yyets
  "字幕组数据库查询服务"
  (:require [clojure.tools.logging :as log]
            [cyberme.db.core :as db]
            [hugsql.core :as hug]))

(defn handle-search
  "美剧查询，目前仅支持返回前 100 条数据"
  [{:keys [q take drop] :or {take 300 drop 0} :as _}]
  (let []
    (try
      {:message "搜索成功"
       :data    (db/yyets-search-basic {:search q
                                        :take   take
                                        :drop   drop})
       :status  1}
      (catch Exception e
        (.printStackTrace e)
        (log/error "[yyets:search] failed: " (.getMessage e))
        {:message (str "未处理的异常：" (.getMessage e)) :status -1}))))

(defn handle-resource
  "返回特定美剧数据"
  [resource-id]
  (let []
    (try
      {:message "查询资源成功"
       :data    (-> (db/yyets-resource {:id resource-id})
                    :data :data)
       :status  1}
      (catch Exception e
        (.printStackTrace e)
        (log/error "[yyets:resource] failed: " (.getMessage e))
        {:message (str "未处理的异常：" (.getMessage e)) :status -1}))))


(comment
  (db/yyets-resource {:id 12013})
  (db/yyets-search-basic {:search "24"}))

(comment
  (hug/def-sqlvec-fns "sql/cyber.sql"))