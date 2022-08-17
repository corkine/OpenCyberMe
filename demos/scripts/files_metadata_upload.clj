;clojure -Sdeps "{:paths [\".\"] :deps {http-kit/http-kit {:mvn/version,\"2.5.0\"} cheshire/cheshire {:mvn/version,\"5.10.0\"}}}" -M -m files-metadata-upload
(ns files-metadata-upload
  "上传磁盘元数据信息。

  需要配置环境变量 CALIBRE_SEC user:token 或 CALIBRE_SEC_SIMPLE user:secret。

  另外可设置 DISK_NAME 和 MACHINE_NAME 表示磁盘和机器名称，一并上传，DISK_NAME
  最好是挂载点的磁盘名，某个文件/文件夹的标识由其路径和磁盘名共同决定。

  windows:
  $env:CALIBRE_SEC = \"user:token\"
  dir env:CALIBRE_SEC

  linux:
  export CALIBRE_SEC=\"user:token\"
  echo $CALIBRE_SEC"
  (:require [cheshire.core :as json]
            [org.httpkit.client :as client]
            [clojure.string :as str])
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

(defn- get-env
  ([key]
   (System/getenv key))
  ([key default]
   (or (System/getenv key) default)))

(defn- get-env-must
  ([key]
   (if-let [res (System/getenv key)]
     res (throw (RuntimeException. (str "无法获取变量" key))))))

(def token
  (let [[user pass] (str/split (or (System/getenv "CALIBRE_SEC") "") #":")]
    (if (or (str/blank? user) (str/blank? pass))
      (let [[user pass] (str/split (or (System/getenv "CALIBRE_SEC_SIMPLE") "") #":")]
        (when (or (str/blank? user) (str/blank? pass))
          (throw (RuntimeException. "没有用户凭证，请设置 CALIBRE_SEC or CALIBRE_SEC_SIMPLE 变量")))
        {:user user :pass (pass-encode pass 500)})
      {:user user :pass pass})))

(def basic
  (merge token
         {:url "https://cyber.mazhangjing.com/cyber/disks/updating-disk-metadata"}))

(def config
  {:path           "/Volumes/新加卷"
   :deps           4
   :upload-disk    (get-env "DISK_NAME" "DISK_NAME")
   :upload-machine (get-env "MACHINE_NAME" "MACHINE1")})

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
                                                        {:upload-at (System/currentTimeMillis)})})})
        resp @req]
    (-> resp :body (json/parse-string true) :message)))

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
                            (when-not (or (str/includes? (.toString file) "$RECYCLE.BIN")
                                          (str/includes? (.toString file) ".DS_Store")
                                          (str/includes? (.toString file) "/新加卷/calibre/"))
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
                                (swap! count inc)))
                            (FileVisitResult/CONTINUE))))
    (println "all file:" (.size list))
    list))

(defn -main []
  (println "running script files-metadata-upload...")
  (let [files (collect-files)]
    (println (send-request! files))))