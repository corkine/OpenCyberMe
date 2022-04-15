(ns cyberme.util.upload
  (:require [ajax.core :as ajax]
            [cyberme.util.request :as req]))

(def image-types #{"image/gif", "image/png", "image/jpeg", "image/bmp",
                   "image/webp", "image/x-icon", "image/vnd.microsoft.icon"})

(defn upload-file
  "上传单个文件，仅允许特定的文件类型，文件大小限制在后端进行 - WebServer 和输入大小验证双重限制。"
  [files callback]
  (let [file (aget files 0)
        first-file-type (.-type file)]
    (if-not (contains? image-types first-file-type)
      (callback {:message "上传文件不是图片类型，请重新选择图片文件。" :status 0 :data nil})
      (let [form (doto (js/FormData.) (.append "file" file))]
        (ajax/POST "/api/files/upload"
                   {:body            form
                    :response-format :json
                    :keywords?       true
                    :headers         (req/auth-header)
                    :handler         (fn [resp] (let [data (js->clj resp :keywordize-keys true)]
                                                  (callback data)))
                    :error-handler   (fn [ctx]
                                       (let [rsp (js->clj (:response ctx) :keywordize-keys true)]
                                         (println rsp)
                                         (callback {:message "上传失败" :status 0 :data nil})))})))))