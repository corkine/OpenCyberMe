(ns cyberme.cyber.psych
  (:require [cyberme.db.core :as db]
            [clojure.tools.logging :as log]
            [next.jdbc :as jdbc]
            [cyberme.cyber.inspur :as inspur]
            [cyberme.tool :as tool]))

(defn add-log
  [data]
  (try
    (db/add-log {:api  "psych-exp-01"
                 :info data})
    {:message "上传成功，现在可以安全关闭此页面。"
     :data nil
     :status 1}
    (catch Exception e
      (log/error "log req error: " (str e))
      {:message (str "上传失败，请联系主试人员：" e)
       :data nil
       :status 0})))