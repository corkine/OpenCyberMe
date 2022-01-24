(ns icemanager.doc
  (:require [docx-utils.core :as doc]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [icemanager.feature :as feature]
            [clojure.string :as string])
  (:import (java.time.format DateTimeFormatter)
           (java.time LocalDateTime)))

(defn gen-tr-doc [feature]
  (println feature)
  (let [{:keys [id rs_id title description
                version info create_at update_at]
         :or  {title "暂无标题"
               description "暂无描述"
               version "ICE 5.0"}} feature
        {:keys [uiRes designRes apiRes developer review implement
                feature-version limit]} info]
    (let [developer (if (vector? developer) developer [developer])
          role-fnn (fn [role developer]
                     (println "role: " role ", dev: " developer)
                     (filter #(string/includes? (get % :role "") role) developer))
          name-fnn (fn [developer] (vec (map #(get % :name) developer)))
          dev-list (name-fnn developer)
          backend-dev-list (name-fnn (role-fnn "后端" developer))
          author (if (empty? backend-dev-list)
                   "佚名" (string/join ", " backend-dev-list))
          frontend-dev-list (name-fnn (role-fnn "前端" developer))]
      (let [pa-length (count implement)
            time (.format ^LocalDateTime update_at DateTimeFormatter/ISO_LOCAL_DATE)
            feature-version (if (string/blank? feature-version) time feature-version)
            ice-version (string/replace version " " "_")
            rs_id (string/upper-case (string/replace rs_id " " "_"))
            full_rs_id (str ice-version "." rs_id)
            short_description (let [first (first (string/split description #"。"))]
                                (if (string/blank? first) "暂无描述" first))]
        (println "rs id" rs_id
                 ", backend-dev-list: " backend-dev-list
                 ", pa-length: " pa-length
                 ", update at: " time))))
  ;title author time feature-version full_rs_id limit short_description
  ;title{1-4} summary{1-4} content{1-4}
  ;request{1-4} response{1-4}
  #_(let [progress (or (re-find #"\w\w:\w\w:\w\w:\w\w:\w\w" name) "")
        simple-name (.trim (.replaceAll name progress ""))
        time (.format DateTimeFormatter/ISO_DATE (LocalDateTime/now))
        short-description (str (.substring info 0 (min 10 (.length info))) "..")
        rs-id (-> id (clojure.string/replace "_" "")
                  (clojure.string/replace "-" "")
                  (clojure.string/upper-case))
        full-rs-id (str "ICE5.0." rs-id)]
    (try (doc/transform "./docs/tr.docx"
                        (vec (map (fn [& [[hold real]]]
                                    {:type :replace-text-inline
                                     :placeholder hold
                                     :replacement real})
                                  [["title" simple-name]
                                   ["time" time]
                                   ["author" "马章竞"]
                                   ["version" "1.0.0"]
                                   ["short_description" short-description]
                                   ["description" info]
                                   ["rs_id" rs-id]
                                   ["full_rs_id" full-rs-id]
                                   ["limit" limit]])))
         (catch Throwable _ nil)))
  "./docs/tr.docx")

(defn resp-tr-doc [rs-id]
  (let [feature (feature/feature-by-rs-id rs-id)
        no-feature (nil? feature)
        _ (if no-feature (log/error "No feature find in db with rs id: " rs-id))
        file-str (if no-feature
                   "./docs/tr.docx" (gen-tr-doc feature))]
    (-> file-str (io/resource) (io/input-stream))))