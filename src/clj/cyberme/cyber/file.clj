(ns cyberme.cyber.file
  "阿里云 OSS 模块"
  (:require [cyberme.tool :refer [bucket]]
            [cyberme.config :refer [edn-in]]
            [cyberme.oss :as oss]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io])
  (:import (java.util UUID)
           (java.time LocalDate)))

(defn handle-upload
  "上传文件到 OSS 并且返回 URL，
  URL 规则：{bucket-url}/{bucket-path}/{current-month}/{short-uuid}_{file-name}"
  ([size filename file]
   (let [max-size (edn-in [:oss :max-size])
         bucket-name (edn-in [:oss :bucket-name])
         bucket-path (edn-in [:oss :bucket-path])
         bucket-url (edn-in [:oss :bucket-url])
         now (LocalDate/now)
         folder-prefix (format "%04d%02d" (.getYear now) (.getMonthValue now))
         file-prefix (second (re-find #"([a-zA-Z0-9]+)-.*?" (str (UUID/randomUUID))))
         file-path (str bucket-path "/" folder-prefix "/" file-prefix "_" filename)
         url-path (str bucket-url "/" file-path)]
     (if (and (not (nil? size)) (> size max-size))
       {:message (str "文件 " filename " 大小超过限制：" (/ max-size 1000) " kb")
        :status  0}
       (try
         (.putObject bucket bucket-name file-path file)
         {:message (str "上传文件 " filename " 成功！")
          :status  1
          :data    url-path}
         (catch Exception e
           (log/error "[OSS] failed to upload " filename ", reason: " (.getMessage e))
           {:message (str "上传失败：" (.getMessage e))
            :status 0})))))
  ([filename file]
   (handle-upload nil filename file)))

#_(defn handle-download
  [filename]
  (let [bucket-name (edn-in [:oss :bucket-name])
        bucket-path (edn-in [:oss :bucket-path])
        bucket-url (edn-in [:oss :bucket-url])
        now (LocalDate/now)
        folder-prefix (format "%04d%02d" (.getYear now) (.getMonthValue now))
        file-prefix (second (re-find #"([a-zA-Z0-9]+)-.*?" (str (UUID/randomUUID))))
        file-path (str bucket-path "/" folder-prefix "/" file-prefix "_" filename)
        url-path (str bucket-url "/" file-path)]
    (try
      (when-let [obj (oss/get-object bucket bucket-name file-path)]
        (io/copy (:content obj) (io/file file-path)))
      {:message (str "上传文件 " filename " 成功！")
       :status  1
       :data    url-path}
      (catch Exception e
        (log/error "[OSS] failed to upload " filename ", reason: " (.getMessage e))
        {:message (str "上传失败：" (.getMessage e))
         :status 0}))))