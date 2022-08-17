;clojure -Sdeps '{:paths ["".""] :deps {http-kit/http-kit {:mvn/version,""2.5.0""} cheshire/cheshire {:mvn/version,""5.10.0""}}}' -M -m calibre-upload
(ns calibre-upload
  "上传 Calibre metadata.db 数据库，需要配置环境变量 CALIBRE_SEC user:token 或 CALIBRE_SEC_SIMPLE user:secret，
  如果 metadata.db 不在当前目录下较浅的层次，那么定义 CALIBRE_SEARCH_PATH 从此处查找 metadata.db，将上传第一个找到的。

  windows:
  $env:CALIBRE_SEC = \"user:token\"
  dir env:CALIBRE_SEC

  linux:
  export CALIBRE_SEC=\"user:token\"
  echo $CALIBRE_SEC"
  (:require [cheshire.core :as json]
            [org.httpkit.client :as client]
            [clojure.string :as str])
  (:import (java.security MessageDigest)
           (java.util Base64)
           (java.nio.file Files Paths FileVisitor FileVisitResult Path)))

;;;;;; common function start ;;;;;
(defn- encode-sha-b64
  "将明文进行 SHA1 和 Base64 加密"
  [^String plain]
  (let [sha (MessageDigest/getInstance "SHA1")]
    (.update sha (.getBytes plain))
    (.encodeToString (Base64/getEncoder)
                     (.digest sha))))

(defn- pass-encode
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

(defn- find-filename-first [path filename level]
  (let [res (atom nil)]
    (Files/walkFileTree (Paths/get path (into-array [""]))
                        #{}
                        level
                        (reify FileVisitor
                          (preVisitDirectory [this dir attrs]
                            (FileVisitResult/CONTINUE))
                          (visitFileFailed [this file exc]
                            (FileVisitResult/CONTINUE))
                          (postVisitDirectory [this dir exc]
                            (FileVisitResult/CONTINUE))
                          (visitFile [this file attrs]
                            (if (= (-> ^Path file (.toFile) (.getName)) filename)
                              (do (println "find db file: " (str file))
                                  (reset! res file)
                                  (FileVisitResult/TERMINATE))
                              (FileVisitResult/CONTINUE)))))
    @res))

;;;;;; common function end ;;;;;

(def ^:dynamic config {})

(def token
  (let [[user pass] (str/split (or (System/getenv "CALIBRE_SEC") "") #":")]
    (if (or (str/blank? user) (str/blank? pass))
      (let [[user pass] (str/split (or (System/getenv "CALIBRE_SEC_SIMPLE") "") #":")]
        (when (or (str/blank? user) (str/blank? pass))
          (throw (RuntimeException. "没有用户凭证，请设置 CALIBRE_SEC or CALIBRE_SEC_SIMPLE 变量")))
        {:user user :pass (pass-encode pass 500)})
      {:user user :pass pass})))

(defn upload! [file]
  (let [req (client/request {:url        (:url config)
                             :method     :post
                             :basic-auth [(:user config) (:pass config)]
                             :multipart  [{:name "truncate" :content "true"}
                                          {:name     "file" :content (clojure.java.io/file file)
                                           :filename "metadata.db"}]})
        resp @req]
    (-> resp :body (json/parse-string true) :message)))

(defn find-and-upload! []
  (let [path (:find-path config)
        level (:find-level config)
        file "metadata.db"]
    (println "finding" file "at" path "with level" level)
    (if-let [file (find-filename-first path file level)]
      (do (println "uploading " (str file))
          (println "Result:" (upload! (str file))))
      (println "Not find file" file "at path" path))))

(defn -main []
  (binding [config
            (merge token
                   {:url        "https://cyber.mazhangjing.com/cyber/books/updating-with-calibre-db" ;file,truncate
                    :find-path  (get-env "CALIBRE_SEARCH_PATH" ".")
                    :find-level 3})]
    (find-and-upload!)))