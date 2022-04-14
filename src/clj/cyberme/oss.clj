(ns cyberme.oss
  (:import [com.aliyun.oss OSSClient ClientException OSSException]
           [java.io File ByteArrayInputStream InputStream FileNotFoundException IOException]
           [com.aliyun.oss.model AppendObjectRequest OSSObject])
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [cyberme.config :refer [env]]))

(defn mk-oss-client
  [endpoint ak sk]
  (OSSClient. ^String endpoint ^String ak ^String sk))

(defn shut-client
  [^OSSClient client]
  (.shutdown client))

(defn call-oss
  [^OSSClient client fun]
  (try
    (fun client)
    (catch FileNotFoundException e
      (log/error "[OSS]" e)
      nil)
    (catch IOException e
      (log/error "[OSS]" e)
      nil)
    (catch ClientException e
      (log/error "[OSS][CLIENT]" e)
      nil)
    (catch OSSException e
      (log/error "[OSS]" e)
      nil)))

;;basic bucket operation

(defn create-bucket
  [^OSSClient client bucket-name]
  (call-oss
    client
    (fn [client]
      (.createBucket client bucket-name))))

(defn get-bucket-location
  [^OSSClient client bucket-name]
  (call-oss
    client
    (fn [client]
      (.getBucketLocation client bucket-name))))

(defn get-bucket-info
  [^OSSClient client bucket-name]
  (call-oss
    client
    (fn [client]
      (bean (.getBucketInfo client bucket-name)))))

(defn delete-bucket
  [^OSSClient client bucket-name]
  (call-oss
    client
    (fn [client]
      (.deleteBucket client bucket-name))))

;;basic object operation
(defn put-file
  [^OSSClient client bucket-name key ^File data-file]
  (call-oss
    client
    (fn [client]
      (.putObject client bucket-name key data-file))))

(defn put-object
  [^OSSClient client bucket-name key ^InputStream data]
  (call-oss
    client
    (fn [client]
      (.putObject client bucket-name key data))))

(defn put-string
  [^OSSClient client bucket-name key ^String data]
  (put-object client bucket-name key (ByteArrayInputStream. (.getBytes data "UTF-8"))))

(defn get-object
  [client bucket-name key]
  (when-let [^OSSObject oss-object (call-oss
                                     client
                                     (fn [client]
                                       (.getObject client bucket-name key)))]
    {:bucket-name bucket-name
     :content (.getObjectContent oss-object)
     :key key}))

(defn get-file
  [^OSSClient client bucket-name key file-path]
  (when-let [obj (get-object client bucket-name key)]
    (io/copy (:content obj) (io/file file-path))))

(defn get-string
  [client bucket-name key]
  (when-let [obj (get-object client bucket-name key)]
    (slurp (:content obj))))

(defn delete-object
  "Delete object"
  [client bucket-name key]
  (call-oss
    client
    (fn [client]
      (.deleteObject client bucket-name key))))