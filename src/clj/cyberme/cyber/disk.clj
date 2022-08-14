(ns cyberme.cyber.disk
  "设计约束：磁盘文件路径不可冲突，多个磁盘默认挂载同一电脑不同路径，如果路径冲突插入操作被看做更新，且 info->disk 信息被重写"
  (:require [clojure.tools.logging :as log]
            [cyberme.db.core :as db]
            [next.jdbc :as jdbc]))

(defn handle-search
  "搜索路径或文件名
  type 1 只搜索文件，查找文件名
  type 2 只搜索文件，查找文件路径
  type 3 搜索文件+文件夹，查找文件路径"
  [{:keys [q type take drop]
    :or {type 1 take 300 drop 0}}]
  (try
    {:message "搜索成功"
     :data    (case type
                1 (db/find-file-by-name {:search q :take take :drop drop})
                2 (db/find-file-by-path {:search q :take take :drop drop})
                3 (db/find-path {:search q :take take :drop drop})
                (throw (RuntimeException. "未指定搜索类型")))
     :status  1}
    (catch Exception e
      (log/error "[disk:search] failed: " (.getMessage e))
      {:message (str "未处理的异常：" (.getMessage e)) :status -1})))

(defn handle-upload
  "files 格式 [{:path :file :last-modified :is-file? :is-path? :size}]
  upload-info 格式 {:upload-at Timestamp :upload-disk 'xxx' :upload-machine 'xxx'}"
  [files upload-info truncate]
  (try
    (let [{:keys [upload-disk] :or {upload-disk "UnknownDisk"}} upload-info
          ;batch order: path name size info
          map-files (mapv (fn [{:keys [path file last-modified type size]}]
                            [path file size {:last-modified last-modified
                                             :type type :disk upload-disk}]) files)]
      (when (empty? map-files)
        (throw (RuntimeException. "没有传入数据，已终止上传")))
      (jdbc/with-transaction
        [t db/*db*]
        {:message (str "上传成功")
         :data (if truncate
                 (do
                   (db/drop-disk-files {:disk upload-disk})
                   (db/insert-files-batch {:files map-files}))
                 (db/insert-files-batch {:files map-files}))
         :status 1}))
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
  (ns files-metadata-upload
    (:require [cheshire.core :as json]
              [org.httpkit.client :as client])
    (:import (java.nio.file FileVisitResult FileVisitor Files Path Paths)
             (java.security MessageDigest)
             (java.util ArrayList Base64)))

  (defn encode-sha-b64
    "将明文进行 SHA1 和 Base64 加密"
    [^String plain]
    (let [sha (MessageDigest/getInstance "SHA1")]
      (.update sha (.getBytes plain))
      (.encodeToString (Base64/getEncoder)
                       (.digest sha))))

  (defn pass-encode
    "将明文密码加密，格式为 BASE64(BASE64(SHA1(PASS::TIMESTAMP))::TIMESTAMP)"
    [plain expired-seconds]
    (let [expired (+ (System/currentTimeMillis) (* expired-seconds 1000))]
      (.encodeToString (Base64/getEncoder)
                       (.getBytes
                         (str (encode-sha-b64 (format "%s::%d" plain expired))
                              "::" expired)))))

  (def config
    {:path           "/Volumes/新加卷"
     :deps           4
     :upload-disk    "WD1TB"
     :upload-machine "MAC_MINI_2018"})

  (def basic
    {:url #_"https://cyber.mazhangjing.com/cyber/disks/updating-disk-metadata"
     "http://localhost:3000/cyber/disks/updating-disk-metadata"
     :user "xxxxx"
     :pass (pass-encode "xxxxx" 1000)})

  (defn send-request!
    "将文件元数据上传"
    [files]
    (let [req
          (client/request {:url     (:url basic)
                           :method  :post
                           :headers {"Content-Type" "application/json"}
                           :basic-auth [(:user basic) (:pass basic)]
                           :body    (json/generate-string
                                      {:truncate    true
                                       :files       files
                                       :upload-info (merge config
                                                           {:upload-at (System/currentTimeMillis)})})})]
      (-> @req :body (json/parse-string true) :message)))

  (defn collect-files []
    (let [count (atom 0) list (ArrayList.)]
      (Files/walkFileTree (Paths/get (:path config) (into-array [""]))
                          #{}
                          (:deps config)
                          (reify FileVisitor
                            (preVisitDirectory [this dir attrs]
                              (FileVisitResult/CONTINUE))
                            (visitFileFailed [this file exc]
                              (FileVisitResult/CONTINUE))
                            (postVisitDirectory [this dir exc]
                              (FileVisitResult/CONTINUE))
                            (visitFile [this file attrs]
                              (let [f (.toFile ^Path file)
                                    is-dir (.isDirectory f)
                                    is-f (.isFile f)
                                    last-modified (.lastModified f)
                                    size (.length f)
                                    path (.toString file)
                                    file-name (.getName f)]
                                (.add list {:file          file-name
                                            :path          path
                                            :last-modified last-modified
                                            :type          (if is-f "FILE" (if is-dir "FOLDER" "UNKNOWN"))
                                            :size          size})
                                (swap! count inc))
                              (FileVisitResult/CONTINUE))))
      (println "all file:" (.size list))
      list))

  (defn -main []
    (let [files (collect-files)]
      (send-request! files))))