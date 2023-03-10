(ns cyberme.cyber.diary
  "日记、植物浇水和周计划汇总 API（周计划另参见 week_plan.clj）"
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [cyberme.cyber.inspur :as inspur]
            [cyberme.cyber.week-plan :as week]
            [cyberme.db.core :as db]
            [cyberme.tool :as tool]
            [hugsql.core :as hug]
            [cyberme.cyber.file :as file]
            [cheshire.core :as json]
            [next.jdbc :as jdbc])
  (:import
    (java.io File)
    (java.nio.file Path Paths)
    (java.time LocalDate LocalDateTime ZonedDateTime)
    (java.time.format DateTimeFormatter)))

(comment
  ;Note
  ;这里之所以不使用类似于前端 ajax-flow 的写法：
  ;(ajax-flow {:call                   :diary/delete-current
  ;            :uri-fn                 #(str "/cyber/diary/" % "/delete")
  ;            :is-post                true
  ;            :data                   :diary/delete-current-data
  ;            :clean                  :diary/delete-current-data-clean
  ;            :success-callback-event [[:diary/delete-current-data-clean]
  ;                                     [:diary/current-data-clean]
  ;                                     [:common/navigate! :diary]]
  ;            :failure-notice         true})
  ;
  ;是因为在一开始，只是简单的 CRUD 某一表的时候，看上去这种精简很棒，写起来也很简单
  ;但这里的函数本质是业务，随着业务的复杂，彼此开始互相关联，就需要引入事务，状态甚至是 RPC
  ;这些内容将会填充到这些 handle-xxx 的函数中以满足业务需求。而前端的 ajax-flow 却非常固定
  ;其本质就是 HTTP 请求后端某一 API 并根据返回数据格式触发不同的事件，业务复杂性提升体现在
  ;对于消息的处理而非发送消息上，比如返回日记列表，更新了 list-data，而如果要根据前端组件过滤
  ;那么会创建一个新的 query 映射 list-data 的数据，如果要统计数据信息，也是基于 list-data
  ;新写一个 query，换言之，前端 ajax-flow 抽象的是从事件到HTTP到返回值到事件的过程，变成了
  ;纯粹的面向事件编程，业务被抽离了出来。而后端如果使用类似抽象，虽然可以一下子从前端事件捅到
  ;后端数据库，而把后端全部架空，但是业务却也没有地方写了：除非面向视图编程，而后者是非常不灵活的。
  ;
  ;// 一种简单 CRUD 架构，放在 .cljc 中
  ;// 后端加载并将其映射到路由和数据库DAO调用，前端加载并且将事件映射为HTTP调用再映射为事件
  ;// HTTP 数据 GET 拼 query，POST 拼 body，时间格式自动在 js/Date 和 java.time 之间映射。
  ;(ajax-flow {:call                   :diary/delete-current
  ;            :uri-fn                 #(str "/cyber/diary/" % "/delete")
  ;            :is-post                true
  ;            :data                   :diary/delete-current-data
  ;            :clean                  :diary/delete-current-data-clean
  ;            :success-callback-event [[:diary/delete-current-data-clean]
  ;                                     [:diary/current-data-clean]
  ;                                     [:common/navigate! :diary]]
  ;            :failure-notice         true
  ;            :sql-func               db/delete-current}))
  )

(defn handle-draft-diary-count
  "获取日记草稿个数"
  []
  (try
    (:count (db/count-diary-draft))
    (catch Exception e
      (.printStackTrace e)
      0)))

(defn handle-diaries-limit
  "获取最近的日记，按照范围获取，from 最小值为 1"
  [{:keys [from to is-super? is-draft?] :or {from 1 to 100}}]
  (try
    {:message (format "获取日记从 %d 到 %d 条成功" from to)
     :data    (let [all (db/range-diary {:drop      (- from 1)
                                         :take      (+ (- to from) 1)
                                         :is-draft? is-draft?})]
                (if is-super? all (filterv #(-> % :info :is-sec? not) all)))
     :status  1}
    (catch Exception e
      {:message (str "按照范围获取最近的日记失败" (.getMessage e)) :status 0})))

(defn handle-diaries-query
  "获取最近的日记，按照范围获取，from 最小值为 1"
  [{:keys [from to search tag year month from-year from-month to-year to-month is-super? is-draft?]
    :or   {from 1 to 100}}]
  (try
    {:message (format "获取日记从 %d 到 %d 条成功" from to)
     :data    (let [all
                    (db/find-diary
                      {:drop      (- from 1)
                       :take      (+ (- to from) 1)
                       :is-draft? is-draft?
                       ;目前只搜索第一个关键词
                       :search    (if (vector? search)
                                    (first search)
                                    search)
                       ;tag 标签目前不搜索，其需要对 JSON 的字段进行模糊匹配
                       ;后期抽取为单独的表
                       :tag       (when tag
                                    (if (vector? tag)
                                      (str/replace (first tag) "#" "")
                                      (str/replace tag "#" "")))
                       :year      year
                       :month     month
                       :from      (cond (and from-year from-month)
                                        (format "%s-%s-%s" from-year from-month 1)
                                        from-year
                                        (format "%s-%s-%s" from-year 1 1)
                                        from-month
                                        (format "%s-%s-%s" (.getYear (LocalDate/now)) from-month 1))
                       :to        (let [to-year1 (when to-year (Integer/parseInt to-year))
                                        to-month1 (when to-month (Integer/parseInt to-month))]
                                    (cond (and to-year1 to-month1)
                                          (format "%s-%s-%s" to-year1 to-month1
                                                  (.getDayOfMonth
                                                    (.minusDays
                                                      (.plusMonths (LocalDate/of to-year1 to-month1
                                                                                 1) 1) 1)))
                                          to-year1
                                          (format "%s-%s-%s" to-year1 12
                                                  (.getDayOfMonth
                                                    (.minusDays
                                                      (.plusMonths (LocalDate/of to-year1 12 1) 1) 1)))
                                          to-month1
                                          (let [year (.getYear (LocalDate/now))]
                                            (format "%s-%s-%s" year to-month1
                                                    (.getDayOfMonth
                                                      (.minusDays
                                                        (.plusMonths (LocalDate/of year to-month1
                                                                                   1) 1) 1))))))})]
                (if is-super? all (filterv #(-> % :info :is-sec? not) all)))
     :status  1}
    (catch Exception e
      (.printStackTrace e)
      {:message (str "按照范围获取最近的日记失败" (.getMessage e)) :status 0})))

(defn handle-diary-by-id
  "获取某一日记"
  [{:keys [id is-super?]}]
  (try
    (if-let [data (db/diary-by-id {:id id})]
      (if is-super?
        {:message "获取成功" :data data :status 1}
        (if (-> data :info :is-sec?)
          {:message "获取失败，没有权限" :data nil :status 0}
          {:message "获取成功" :data data :status 1}))
      {:message (str "获取失败，没有找到 id 为 " id " 的日记。")
       :status  0})
    (catch Exception e
      {:message (str "获取日记 #" id " 失败" (.getMessage e))
       :status  0})))

(defn handle-diary-by-day
  "获取某一天的日记，天数使用 2022-03-01 格式传入"
  [{:keys [date is-super?]}]
  (try
    (let [day-inst (LocalDate/parse
                     date
                     (DateTimeFormatter/ISO_LOCAL_DATE))
          data (db/diaries-by-day {:day day-inst})]
      (if is-super?
        {:message "获取成功" :data data :status 1}
        (if (-> data :info :is-sec?)
          {:message "获取失败" :data nil :status 0}
          {:message "获取成功" :data data :status 1})))
    (catch Exception e
      {:message (str "获取日记 @" date " 失败" (.getMessage e))
       :status  0})))

(defn handle-diary-by-label
  "获取某一标签的日记"
  [{:keys [label]}]
  (try
    {:message "获取成功"
     :data    (db/diaries-by-label {:label label})
     :status  1}
    (catch Exception e
      {:message (str "获取日记 Tag #" label " 失败" (.getMessage e))
       :status  0})))

(defn handle-diary-delete
  "删除某一日记"
  [{:keys [id]}]
  (try
    {:message "删除成功"
     :status  1
     :data    (db/delete-diary {:id id})}
    (catch Exception e
      {:message (str "删除日记 #" id " 失败" (.getMessage e))
       :status  0})))

(defn handle-insert-diary
  "添加一篇日记，info->score 中可能有当日评分，将其加入 days 表中"
  [{:keys [title content info]}]
  (try
    (jdbc/with-transaction
      [t db/*db*]
      (let [day (or (try
                      (LocalDate/parse (:day info) (DateTimeFormatter/ISO_LOCAL_DATE))
                      (catch Exception e
                        (log/error "[diary-save] parse (:day info) error: "
                                   info ", e: " (.getMessage e))
                        nil)) (LocalDate/now))
            insert-diary-action
            (db/insert-diary t
                             {:title   title
                              :content content
                              :info    (assoc info
                                         :is-sec? (str/ends-with? (or title "") "__"))})]
        (if-let [score (:score info)]
          {:message "添加成功并成功更新每日分数"
           :status  1
           :data    {:diary insert-diary-action
                     :score (db/set-someday-info t {:day day :info {:score score}})}}
          {:message "添加成功"
           :status  1
           :data    {:diary insert-diary-action}})))
    (catch Exception e
      {:message (str "添加日记 " title " 失败： " (.getMessage e))
       :status  0})))

(defn handle-update-diary
  "更新一篇日记，info->score 中可能有当日评分，将其加入 days 表中"
  [{:keys [title content info id]}]
  (try
    (jdbc/with-transaction
      [t db/*db*]
      (let [day (or (try
                      (LocalDate/parse (:day info) (DateTimeFormatter/ISO_LOCAL_DATE))
                      (catch Exception e
                        (log/error "[diary-save] parse (:day info) error: "
                                   info ", e: " (.getMessage e))
                        nil)) (LocalDate/now))
            update-action
            (db/update-diary t {:title   title
                                :content content
                                :info    (if (str/ends-with? (or title "") "__")
                                           (assoc info :is-sec? true)
                                           (dissoc info :is-sec?))
                                :id      id})]
        (if-let [score (:score info)]
          {:message "更新成功并成功更新每日分数"
           :status  1
           :data    {:diary update-action
                     :score (db/set-someday-info t {:day day :info {:score score}})}}
          {:message "更新成功"
           :status  1
           :data    {:diary update-action}})))
    (catch Exception e
      {:message (str "更新日记 " title " 失败： " (.getMessage e))
       :status  0})))

(comment
  {:id        int?
   :title     string?
   :content   string?
   :info      {:labels list?
               :score  int?
               :day    string?                              ;date
               }
   :create_at string?
   :update_at string?}
  (db/bind))

(defn handle-day-work
  "查找 day 数据库获取当日日报信息，如果非工作日，则直接返回不查找数据库"
  []
  (let [is-workday? (inspur/do-need-work (LocalDateTime/now))]
    (if is-workday?
      {:message "获取成功"
       :data    (-> (db/today) :info :day-work)
       :status  1}
      {:message "获取成功(无需工作)"
       :data    "无需日报"
       :status  1})))

(defn have-finish-daily-report-today?
  "查找 day 数据库获取当日日报信息，如果非工作日，则直接返回不查找数据库"
  []
  (let [is-workday? (inspur/do-need-work (LocalDateTime/now))]
    (if is-workday?
      (str/includes? (or (-> (db/today) :info :day-work) "")
                     "已完成")
      true)))

(defn handle-day-work-update [data]
  (let [res (db/set-someday-info {:day (LocalDate/now) :info {:day-work data}})]
    {:message (str "更新成功: " res)
     :status  1}))

(defn handle-plant-learn-week
  "查找 day 数据库获取本周浇花和学习情况，返回 {:status [0 1 0 1 0 0], :learn :done/:not-done}"
  []
  (let [this-week (tool/all-week-day)
        week-info (db/day-range {:from (first this-week) :to (last this-week)})
        week-info-map (reduce #(assoc %1 (:day %2) %2) {} week-info)
        full-week-info (map #(get week-info-map % {}) this-week)
        status (mapv #(if (nil? (-> % :info :plant)) 0 1) full-week-info)
        ;learn-done (some #(-> % :info :learn nil? not) full-week-info)
        ;每周可能有多次学习，每次学习开始，标记当天 :learn-request true, 上一个学习
        ;完成，标记当天 :learn-done true，这里的 true 无意义，只要存在 key 即可
        ;学习完成意味着每周的 :learn-request count= :learn-done
        count-learn-req (count (filterv #(-> % :info :learn-request nil? not) full-week-info))
        count-learn-done (count (filterv #(-> % :info :learn-done nil? not) full-week-info))
        learn-done (= count-learn-req count-learn-done)]
    {:message "获取成功"
     :data    {:status    status
               :learn     (if learn-done :done :not-done)
               :week-plan (week/handle-get-week-plan)}
     :status  1}))

(defn handle-plant-week-update-today
  "更新当日浇花情况（浇了改为没浇，没浇改为浇了），并查找 day 数据库获取本周浇花情况，返回 {:status [0 1 0 1 0 0]}"
  [data]
  (try
    (let [today-info (:info (db/today))
          non-plant? (nil? (:plant today-info))]
      (db/set-someday {:day  (LocalDate/now)
                       :info (if non-plant?
                               (assoc today-info :plant 1)
                               (dissoc today-info :plant))})
      (let [this-week (tool/all-week-day)
            week-info (db/day-range {:from (first this-week) :to (last this-week)})
            week-info-map (reduce #(assoc %1 (:day %2) %2) {} week-info)
            full-week-info (map #(get week-info-map % {}) this-week)
            status (mapv #(if (nil? (-> % :info :plant)) 0 1) full-week-info)]
        {:message (format "之前%s, 现已更新为%s。" (if non-plant? "没浇花" "浇花了")
                          (if-not non-plant? "没浇花" "浇花了"))
         :data    {:status status}
         :status  1}))
    (catch Exception e
      (log/error e)
      {:message (str "遇到了一些错误: " (.getMessage e))
       :data    {:status [0 0 0 0 0 0]}
       :status  0})))

(defn handle-set-today-learn
  "设置今日开始、结束一项学习"
  [{:keys [start non-start end non-end]}]
  (try
    (jdbc/with-transaction
      [t db/*db*]
      (let [today-info (:info (db/today t))
            today-info (if start (assoc today-info :learn-request true) today-info)
            today-info (if non-start (dissoc today-info :learn-request) today-info)
            today-info (if end (assoc today-info :learn-done true) today-info)
            today-info (if non-end (dissoc today-info :learn-done) today-info)]
        ;如果本周当前已经平衡，则不允许设置 end 为 true，换言之没有 request 去 end
        (if (and end
                 (= :done (-> (handle-plant-learn-week) :data :learn)))
          (throw (RuntimeException. "没有尚未完成的任务需要标记完成。")))
        (db/set-someday t {:day (LocalDate/now) :info today-info})
        {:message (format "更新当日学习成功。 req: %s, done: %s"
                          (:learn-request today-info)
                          (:learn-done today-info))
         :status  1}))
    (catch Exception e
      {:message (format "更新当日学习失败。 %s" e)
       :status  0})))

;;;;;;;;;;;;; DAY ONE IMPORTER ;;;;;;;;;;;;;
(defn formatted-day-one-data
  "从 DayOne 导出的 zip 压缩包中解析数据，将图片调换为 OSS URL 并更新文本

  {:metadata {:version string?}
   :entries  [{:isPinned      boolean?
               :starred       boolean?
               :isAllDay      boolean?
               :weather?      {:weatherServiceName    'Forecast.io'
                               :conditionsDescription 'Clear'
                               :visibilityKM          double?
                               :relativeHumidity      int?
                               :weatherCode           'clear'
                               :temperatureCelsius    double?}
               :creationDate  '2017-09-13T14:38:14Z'
               :modifiedDate  '2017-09-13T14:38:14Z'
               :timeZone      'Asia/Shanghai'
               :tags?         [string?]
               :richText?     string?                       ;兼容字段
               :text          string?                       ;换行有的用 \n 有的用 \r\n
               ;其中的 ![](dayone-moment://2C9694443FAE45FEBE2774B180217005)
               ;对应 photo 的 identifier 字段，而 photo 的 md5 和 type 对应文件名
               :uuid          string?
               :duration      int?
               :location?     {:region             {:center {:longitude double?
                                                             :latitude  double?}
                                                    :radius int?}
                               :localityName       string?
                               :country            string?
                               :longitude          double?
                               :administrativeArea string?
                               :placeName          string?
                               :latitude           double?}
               :userActivity? {:activityName string?}
               :photos?       [{:orderInEntry      int?
                                :creationDevice    string?
                                :duration          int?
                                :favorite          boolean?
                                :type              'jpeg'
                                :identifier        uuid?
                                :exposureBiasValue int?
                                :height            double?
                                :width             double?
                                :md5               string?
                                :isSketch          boolean?}]}]}
  "
  [dir]
  (let [dir (or dir "C:\\Users\\mazhangjing\\Downloads\\12-12-2022_11-49-下午")
        json-filename "Journal.json"
        photos-dirname "photos"]
    (let [dir-path (Paths/get dir (into-array [""]))
          json-path (.resolve dir-path json-filename)
          photos-path (.resolve dir-path photos-dirname)
          photo-file (fn [{:keys [md5 type]}]
                       (.toFile (.resolve photos-path (str md5 "." type))))
          photo-file-map (fn [photos]
                           (reduce (fn [agg {:keys [identifier] :as photo}]
                                     (assoc agg identifier (photo-file photo))) {} photos))]
      (mapv
        (fn [{:keys [photos text] :as diary}]
          (let [pm (photo-file-map (or photos []))
                moment->ossUrl
                (filter (comp not nil?)
                        (mapv
                          (fn [moment-url]
                            (when-let [target-file ^File (get pm (second (re-find #"//(\w+)" moment-url)))]
                              [moment-url (format "![](https://static2.mazhangjing.com/dayone/%s)"
                                                  (.getName target-file))]))
                          (re-seq #"\!\[\]\(dayone-moment:.*?\)" text)))
                replaced-text                               ;处理过图片的 Markdown 文本
                (reduce (fn [text [mo oss]] (str/replace text mo oss)) text moment->ossUrl)]
            (-> (dissoc diary :richText)
                (assoc :text replaced-text))))
        (-> (slurp (.toString json-path)) (json/parse-string true) :entries)))))

(defn convert-day-one-json->db
  "将 day one 数据提交到数据库，处理逻辑：
  text 日记文本在上一步替换照片为 OSS URL 后，在这一步删除 \r，并且视图从第一行提取标题，如果提取
  成功，则删除标题，其余作为正文，反之使用 DayOne #2022-01-01 作为标题，分别作为 title 和 content 插入。
  tag 添加 DayOne 标签，作为 info->labels 插入。
  creationDate 作为 create_at 插入。
  modifiedDate 作为 update_at 插入。
  除了文本 text、标签 tag 的其余 json 字段作为 info->dayone-origin 插入。

  diary 数据格式：
  id, title, content, info {day: 2022-04-20, score: 90, labels: [工作]}, create_at, update_at

  select count(*) from diary;

  select * from diary
  order by info -> 'day' desc limit 10;

  select count(*) from diary
  where info -> 'dayone-origin' is not null;

  select * from diary
  where info -> 'dayone-origin' is null;

  delete from diary
  where info -> 'dayone-origin' is not null;
  "
  [dayone]
  (mapv (fn [{:keys [text creationDate modifiedDate tags]
              :or {text ""
                   creationDate "2023-01-01T15:00:00Z"
                   modifiedDate (ZonedDateTime/now)
                   tags []} :as day-item}]
          (let [date (.toLocalDateTime (ZonedDateTime/parse creationDate))
                date2 (.toLocalDateTime (ZonedDateTime/parse modifiedDate))
                simple-date (.format date (DateTimeFormatter/ofPattern "yyyyMMdd"))
                info-date (.format date (DateTimeFormatter/ofPattern "yyyy-MM-dd"))
                text (str/replace text #"\r" "")
                text-lines (str/split text #"\n")
                first-line-of-text (first text-lines)
                may-be-title? (< 1 (count first-line-of-text) 20)]
            {:title     (if may-be-title? (-> first-line-of-text
                                              (str/replace "# " "")
                                              (str/replace "#" ""))
                                          (str "DayOne #" simple-date))
             :content   (if may-be-title? (str/join "\n" (rest text-lines))
                                          text)
             :create_at date
             :update_at date2
             :info      {:day           info-date
                         :dayone-origin (-> day-item (dissoc :tags) (dissoc :text))
                         :labels        (conj tags "DayOne")}})) dayone))

(comment
  (mapv :title (convert-day-one-json->db (formatted-day-one-data nil)))
  (doseq [diary (convert-day-one-json->db (formatted-day-one-data nil))]
    (db/insert-diary-full diary))
  (hug/def-sqlvec-fns "sql/cyber.sql")
  )



