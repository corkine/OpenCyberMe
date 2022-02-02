(ns icemanager.doc
  (:require [docx-utils.core :as doc]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [icemanager.feature :as feature]
            [clojure.string :as string]
            [clj-pdf.core :refer [pdf template]]
            [ring.util.http-response :as response])
  (:import (java.time.format DateTimeFormatter)
           (java.time LocalDateTime)
           (java.io ByteArrayInputStream ByteArrayOutputStream)
           (java.time.format DateTimeFormatter)))

(defn gen-tr-doc [feature]
  (let [{:keys [id rs_id title description
                version info create_at update_at]
         :or   {title       "暂无标题"
                description "暂无描述"
                version     "ICE 5.0"}} feature
        {:keys [uiRes designRes apiRes developer review implement
                feature-version limit api]
         :or   {limit "无约束。"}} info]
    (let [developer (if (vector? developer) developer [developer])
          role-fnn (fn [role developer]
                     (filter #(string/includes? (get % :role "") role) developer))
          name-fnn (fn [developer] (vec (map #(get % :name) developer)))
          dev-list (name-fnn developer)
          backend-dev-list (name-fnn (role-fnn "后端" developer))
          author (if (empty? backend-dev-list)
                   "佚名" (string/join ", " backend-dev-list))
          frontend-dev-list (name-fnn (role-fnn "前端" developer))]
      (let [time (.format ^LocalDateTime update_at DateTimeFormatter/ISO_LOCAL_DATE)
            feature-version (if (string/blank? feature-version) time feature-version)
            ice-version (string/replace version " " "_")
            rs_id (string/upper-case (string/replace rs_id " " "_"))
            full_rs_id (str ice-version "." rs_id)
            short_description (let [first (first (string/split description #"。"))]
                                (if (string/blank? first) "暂无描述" first))]
        (let [default-pa {:title "未命名" :summary "" :content ""}
              {title1 :title summary1 :summary content1 :content} (nth implement 0 default-pa)
              {title2 :title summary2 :summary content2 :content} (nth implement 1 default-pa)
              {title3 :title summary3 :summary content3 :content} (nth implement 2 default-pa)
              {title4 :title summary4 :summary content4 :content} (nth implement 3 default-pa)
              content4 (if (> (count implement) 4)
                         (str content4
                              (string/join
                                "\n"
                                (map (fn [{:keys [title summary content]}]
                                       (str "#" title "\n\n" summary "\n\n" content "\n\n"))
                                     (drop 4 implement))))
                         content4)]
          (let [req-resp-row (fn [{:keys [name type example description]}]
                               (format "%-15s %-10s = %-15s %s"
                                       name (str ": [" type "]")
                                       (if (string/blank? example) "???" example)
                                       (if (string/blank? description) "" (str ";; " description)))
                               [name type
                                (if (string/blank? example) "???" example)
                                (if (string/blank? description) "" (str "" description))])
                format-fnn (fn [{:keys [name note path method request response]
                                 :or   {method "GET" request [] response []}
                                 :as   api-now}]
                             (format "%s %s\n%s\n%s\n请求格式：\n%s\n响应格式：\n%s\n"
                                     (string/upper-case method) path
                                     name note
                                     (string/join "\n" (vec (map req-resp-row request)))
                                     (string/join "\n" (vec (map req-resp-row response))))
                             [{:text (str (string/upper-case method) " " path) :bold true}
                              name
                              note
                              {:text "请求格式：" :bold true}
                              (concat [[{:text "字段" :bold true} {:text "类型" :bold true}
                                        {:text "示例值" :bold true} {:text "注释" :bold true}]]
                                      (vec (map req-resp-row request)))
                              {:text "响应格式：" :bold true}
                              (concat [[{:text "字段" :bold true} {:text "类型" :bold true}
                                        {:text "示例值" :bold true} {:text "注释" :bold true}]]
                                      (vec (map req-resp-row response)))])
                api-fnn #(let [now (nth api % nil)] (if (nil? now) "" (format-fnn now)))
                doc-map #(let [data (api-fnn %)]
                           (if (empty? data)
                             (vec (map (fn [index]
                                         {:type        :replace-text
                                          :placeholder (str "api" (inc %) "-" (inc index))
                                          :replacement ""}) (range 7)))
                             (vec (map (fn [index]
                                         (if (= index 4)
                                           {:type        :replace-table
                                            :placeholder (str "api" (inc %) "-" (inc index))
                                            :replacement (nth data index)}
                                           {:type        :replace-text
                                            :placeholder (str "api" (inc %) "-" (inc index))
                                            :replacement (nth data index)})) (range (count data))))))
                api1 (doc-map 0)
                api2 (doc-map 1)
                api3 (doc-map 2)
                api4 (doc-map 3)]
            (try (doc/transform (str (io/as-file (io/resource "docs/tr.docx")))
                                (concat
                                  (vec (map (fn [& [[hold real type]]]
                                              {:type        (or type :replace-text-inline)
                                               :placeholder hold
                                               :replacement real})
                                            [["title" title] ["author" author] ["time" time]
                                             ["feature-version" feature-version]
                                             ["full_rs_id" full_rs_id]
                                             ["limit" limit]
                                             ["description" description]
                                             ["short_description" short_description]
                                             ["title1" title1] ["title2" title2]
                                             ["title3" title3] ["title4" title4]
                                             ["summary1" summary1] ["summary2" summary2]
                                             ["summary3" summary3] ["summary4" summary4]
                                             ["content1" content1] ["content2" content2]
                                             ["content3" content3] ["content4" content4]
                                             ;["api1" api1 :replace-text] ["api2" api2 :replace-text]
                                             ;["api3" api3 :replace-text] ["api4" api4 :replace-text]
                                             ]))
                                  api1 api2 api3 api4))
                 (catch Throwable e
                   (log/error "Something error when render docx: " e)
                   nil))))))))

(defn resp-tr-doc [rs-id]
  (let [feature (feature/feature-by-rs-id rs-id)
        no-feature (nil? feature)
        _ (if no-feature (log/error "No feature find in db with rs id: " rs-id
                                    ", download temp file.."))
        doc-path (if no-feature nil (gen-tr-doc feature))]
    (if doc-path
      (-> doc-path (io/as-file) (io/input-stream))
      (-> "./docs/tr.docx" (io/resource) (io/input-stream)))))

(def review-temp-1
  (template
    [:paragraph
     "\n"
     [:chunk {:style :bold} "RS ID: "] $rs_id "\n"
     [:chunk {:style :bold} "引入版本: "] $ice-version "\n"
     [:chunk {:style :bold} "文档版本: "] $version "\n"
     "\n" $description "\n\n"
     [:chunk {:style :bold} "API 接口: "] "\n"
     [:phrase {:size   8
               :family "serif"
               :color  [50 50 50]}
      $api]
     "\n"
     [:chunk {:style :bold} "评审记录: "] "\n"
     [:chunk {:style :normal :size 8
              :color [50 50 50]}
      $review]
     "\n\n"
     [:chunk {:style :bold} "生成日期: "] $time
     [:spacer]]))

(defn gen-review-doc [feature out]
  (let [{:keys [id rs_id title description
                version info create_at update_at]
         :or   {title       "暂无标题"
                description "暂无描述"
                version     "ICE 5.0"}} feature
        {:keys [uiRes designRes apiRes developer review implement
                feature-version limit api]
         :or   {limit "无约束。"}} info]
    (let [developer (if (vector? developer) developer [developer])
          role-fnn (fn [role developer]
                     (filter #(string/includes? (get % :role "") role) developer))
          name-fnn (fn [developer] (vec (map #(get % :name) developer)))
          backend-dev-list (name-fnn (role-fnn "后端" developer))
          author (if (empty? backend-dev-list)
                   "佚名" (string/join ", " backend-dev-list))]
      (let [time (.format ^LocalDateTime update_at DateTimeFormatter/ISO_LOCAL_DATE)
            feature-version (if (string/blank? feature-version) time feature-version)
            ice-version (string/replace version " " "_")
            rs_id (string/upper-case (string/replace rs_id " " "_"))
            short_description (let [first (first (string/split description #"。"))]
                                (if (string/blank? first) "暂无描述" first))]
        (let [format-review-fnn (fn [{:keys [date title content participants]
                                      :or   {participants [] title "" content ""}}]
                                  (format "# %s @ %s\n%s\n参会人员：%s"
                                          title date content (string/join ", " participants)))
              review-all (string/join "\n\n" (vec (map format-review-fnn review)))]
          (let [req-resp-row (fn [{:keys [name type example description]}]
                               (format "%s %s = %s %s"
                                       name (str ": [" type "]")
                                       (if (string/blank? example) "???" example)
                                       (if (string/blank? description) "" (str " ;; " description))))
                format-fnn (fn [{:keys [name note path method request response]
                                 :or   {method "GET" request [] response []}}]
                             (format "%s %s\n%s\n%s\n请求格式：\n%s\n响应格式：\n%s"
                                     (string/upper-case method) (or path "")
                                     (or name "") (or note "")
                                     (string/join "\n" (vec (map req-resp-row request)))
                                     (string/join "\n" (vec (map req-resp-row response)))))
                api-all (string/join "\n\n" (vec (map format-fnn api)))]
            (pdf [{:font                   {:encoding :unicode
                                            :ttf-name (str (io/as-file (io/resource "docs/sans.ttf")))}
                   :title                  (str "Review for ICE feature: " title)
                   :size                   :a4
                   :author                 author
                   :creator                "ice manager by corkine ma"
                   ;:orientation   :landscape
                   :subject                short_description
                   :header                 "Inspur Cisco Networking Technology. CO.LTD"
                   :footer                 {:text         (str "" title " / ")
                                            :align        :right
                                            :start-page   1
                                            :page-numbers true
                                            }
                   :register-system-fonts? true
                   ;:letterhead [(str "ICE Feature：" rs_id)]
                   }
                  [:heading {:style {:size 17}} title]
                  (review-temp-1 [{:title       title
                                   :description description
                                   :rs_id       rs_id
                                   :ice-version version
                                   :version     feature-version
                                   :time        time
                                   :review      review-all
                                   :api         api-all}])]
                 out)))))))

(defn write-response [report-bytes]
  (with-open [in (ByteArrayInputStream. report-bytes)]
    (-> (response/ok in)
        (response/header "Content-Disposition" "filename=document.pdf")
        (response/header "Content-Length" (count report-bytes))
        (response/content-type "application/pdf"))))

(defn gen-report [feature-info]
  (try (let [out (ByteArrayOutputStream.)]
         (gen-review-doc feature-info out)
         (write-response (.toByteArray out)))
       (catch Exception ex
         (log/error "failed render report! " ex)
         (response/ok (str "failed render report: " ex)))))

(defn resp-review-pdf [rs-id]
  (let [feature (feature/feature-by-rs-id rs-id)
        no-feature (nil? feature)]
    (if no-feature
      (do
        (log/error "No feature find in db with rs id: " rs-id)
        (response/ok (str "failed: No feature find in db with this rs_id.")))
      (gen-report feature))))