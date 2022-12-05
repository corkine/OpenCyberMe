(ns cyberme.media.disk
  "设计约束：磁盘文件路径不可冲突，多个磁盘默认挂载同一电脑不同路径，如果路径冲突插入操作被看做更新，且 info->disk 信息被重写"
  (:require [clojure.tools.logging :as log]
            [cyberme.db.core :as db]
            [cyberme.file-share :refer [file-query-range file-query-range-size]]
            [next.jdbc :as jdbc]
            [hugsql.core :as hug]
            [org.httpkit.client :as http]
            [cyberme.config :refer [edn-in]]
            [clojure.string :as str]
            [cheshire.core :as json]))

(defn handle-short-search
  "搜索 go.mazhangjing.com 短链接"
  [query]
  (try
    (let [req (http/request {:url (str "https://go.mazhangjing.com/searchjson/" query)
                             :method :get
                             :content-type :json
                             :basic-auth (edn-in [:short :token])})
          resp @req]
      (let [response (-> resp :body (json/parse-string true))]
        (if (:message response)
          {:message (str "搜索失败，远程服务器返回消息：" (:message response))
           :status -1}
          {:message "搜索成功"
           :status 1
           :data (vec response)})))
    (catch Exception e
      {:message (str "搜索失败" (.getMessage e)) :status -1})))

(defn handle-search
  "搜索路径或文件名"
  ;see file_share.cljc
  [{:keys [q take drop] :or {take 300 drop 0} :as all}]
  (let [default (into {} (for [[k v] file-query-range] [k (first v)]))
        all (merge default all)
        desc? (not (str/includes? (get all :sort "") "早"))
        regex? (str/includes? (get all :kind "") "正则")
        full-path? (str/includes? (get all :range-x "") "路径")
        disk-name (if (str/blank? (get all :range-y "")) nil (:range-y all))
        size-range (let [input (get all :size "")
                         find (filter #(= (first %) input)
                                    file-query-range-size)]
                     (if (empty? find) nil (second (first find))))]
    #_(println "searching for " all
             "gen " {:search q
                     :take take :drop drop
                     :by-path full-path?
                     :desc desc?
                     :disk disk-name
                     :regex regex?
                     :size-from (first size-range)
                     :size-to (last size-range)}
             "sql is " (find-file-sqlvec {:search q
                                          :take take :drop drop
                                          :by-path full-path?
                                          :desc desc?
                                          :disk disk-name
                                          :regex regex?
                                          :size-from (first size-range)
                                          :size-to (last size-range)}))
    (try
      {:message "搜索成功"
       :data    (db/find-file {:search q
                               :take take :drop drop
                               :by-path full-path?
                               :desc desc?
                               :disk disk-name
                               :regex regex?
                               :size-from (first size-range)
                               :size-to (last size-range)})
       :status  1}
      (catch Exception e
        (.printStackTrace e)
        (log/error "[disk:search] failed: " (.getMessage e))
        {:message (str "未处理的异常：" (.getMessage e)) :status -1}))))

(defn handle-upload
  "files 格式 [{:path :file :last-modified :is-file? :is-path? :size}]
  upload-info 格式 {:upload-at Timestamp :upload-disk 'xxx' :upload-machine 'xxx'}"
  [files upload-info truncate]
  (try
    (let [{:keys [upload-disk] :or {upload-disk "UnknownDisk"}} upload-info
          ;batch order: path name size info
          map-files (mapv (fn [{:keys [path file last-modified type size]}]
                            [path file size {:last-modified last-modified
                                             :type          type :disk upload-disk}]) files)]
      (when (empty? map-files)
        (throw (RuntimeException. "没有传入数据，已终止上传")))
      (jdbc/with-transaction
        [t db/*db*]
        {:message (str "上传成功")
         :data    (if truncate
                    (do
                      (db/drop-disk-files {:disk upload-disk})
                      (db/insert-files-batch {:files map-files}))
                    (db/insert-files-batch {:files map-files}))
         :status  1}))
    (catch Exception e
      (.printStackTrace e)
      (log/error "[disk:upload] failed: " (.getMessage e))
      {:message (str "未处理的异常：" (.getMessage e)) :status -1})))


(comment
  (db/insert-files-batch {:files [["abcd" "corkine" "book1" {:last_modified "xxx"
                                                             :path2         "xxx/yyy/zzz"}]
                                  ["abcde" "corkine" "book1" {:last_modified "xxx"
                                                              :path          "xxx/yyy/zzz"}]]})
  (db/find-file {:search "corkine"})
  (db/find-file-by-path {:search "corkine"})
  (db/get-file {:id "001a"})
  (db/drop-all-files)
  (db/drop-disk-files {:disk "cmp1"}))

(comment
  (hug/def-sqlvec-fns "sql/cyber.sql"))