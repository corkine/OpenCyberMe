(ns cyberme.cyber.disk
  (:require [clojure.tools.logging :as log]
            [cyberme.db.core :as db]
            [next.jdbc :as jdbc]))

(defn handle-search
  "搜索路径或文件名"
  [{:keys [q type just-folder just-file]
    :or {just-folder false just-file false type :path}}]
  (try
    {:message "搜索成功"
     :data    (case type
                :path (cond just-folder (db/find-file-by-path-folder {:search q})
                            just-file (db/find-file-by-path-file {:search q})
                            :else (db/find-file-by-path {:search q}))
                :name (cond just-folder (db/find-file-by-name-folder {:search q})
                            just-file (db/find-file-by-name-file {:search q})
                            :else (db/find-file-by-name {:search q}))
                :unify (cond just-folder (db/find-file-folder {:search q})
                             just-file (db/find-file-file {:search q})
                             :else (db/find-file {:search q}))
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
    (let [{:keys [disk] :or {disk "UnknownDisk"}} upload-info
          ;batch order: path name size info
          map-files (mapv (fn [{:keys [path file last-modified type size]}]
                            [path file size {:last-modified last-modified
                                             :type type :disk disk}]) files)]
      (jdbc/with-transaction
        [t db/*db*]
        {:message (str "上传成功")
         :data (if truncate
                 (do
                   (db/drop-disk-files {:disk disk})
                   (db/insert-files-batch {:files map-files}))Ï
                 (db/insert-files-batch {:files map-files}))
         :status 1}))
    (catch Exception e
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

  (def config
    {:path           "/Volumes/新加卷"
     :deps           4
     :upload-disk    "WD1TB"
     :upload-machine "MAC_MINI_2018"})

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

  (defn send-request!
    "将文件元数据上传"
    [files]
    (let [req
          (client/request {:url     "https://cyber.mazhangjing.com/cyber/disks/updating-disk-metadata"
                           :method  :post
                           :headers {"Content-Type" "application/json"}
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