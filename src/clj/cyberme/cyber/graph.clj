(ns cyberme.cyber.graph
  "Microsoft TODO 模块"
  (:require [cheshire.core :as json]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [cyberme.config :refer [edn-in]]
            [cyberme.cyber.slack :as slack]
            [cyberme.db.core :as db]
            [cyberme.info.ticket :as ticket]
            [cyberme.media.news :as news]
            [cyberme.tool :as tool]
            [org.httpkit.client :as client])
  (:import (java.time LocalDate LocalDateTime)
           (java.time.format DateTimeFormatter)))

;访问 mazhangjing.com/todologin 登录微软账户，然后其回调 mazhangjing.com/todocheck
;跳转到 ip/t odo/setcode?code=xxxx，这里保存 code 参数并触发更新缓存操作：/t odo/today。
(def token-url "https://login.microsoftonline.com/common/oauth2/v2.0/token")
(def redirect-url "https://mazhangjing.com/todocheck")
(def list-url "https://graph.microsoft.com/v1.0/me/todo/lists")
(def task-in-list-url "https://graph.microsoft.com/v1.0/me/todo/lists/%s/tasks")
(def outlook-rail-check-url "https://graph.microsoft.com/v1.0/me/messages?$search=%2212306@rails.com.cn%22")

;;;;;;;;;; GRAPH SERVICE ;;;;;;;;

(defonce cache (atom {}))

(defn set-cache [at rt]
  (swap! cache merge {:access-token  at
                      :at-expired    (.plusSeconds (LocalDateTime/now) 3500)
                      :refresh-token rt
                      :rt-expired    (.plusHours (LocalDateTime/now) 24)}))

(defn fetch-cache []
  (let [{:keys [access-token at-expired
                refresh-token rt-expired]} @cache
        now (LocalDateTime/now)
        at-expired (if (string? at-expired)
                     (LocalDateTime/parse
                       at-expired
                       (DateTimeFormatter/ISO_LOCAL_DATE_TIME))
                     at-expired)
        rt-expired (if (string? rt-expired)
                     (LocalDateTime/parse
                       rt-expired
                       (DateTimeFormatter/ISO_LOCAL_DATE_TIME))
                     rt-expired)
        at-out? (or (nil? at-expired) (.isBefore at-expired now))
        rt-out? (or (nil? rt-expired) (.isBefore rt-expired now))]
    {:access-token  (if at-out? nil access-token)
     :refresh-token (if rt-out? nil refresh-token)}))

(defn set-code [code]
  (let [req (client/request {:url         token-url
                             :method      :post
                             :form-params {"client_id"     (edn-in [:graph :client-id])
                                           "scope"         (edn-in [:graph :scope])
                                           "code"          code
                                           "redirect_uri"  redirect-url
                                           "grant_type"    "authorization_code"
                                           "client_secret" (edn-in [:graph :secret])}})
        {:keys [status body] :as all} @req]
    (if (= status 200)
      (let [{:keys [access_token refresh_token] :as body-s}
            (json/parse-string body true)]
        (set-cache access_token refresh_token)
        (log/info "[todo-set-code] done set token by set-code to cache.")
        {:message "Set Code Done."
         :data    body-s
         :status  1})
      (do
        (log/warn "[todo-set-code] todo server call set-code return error: " all)
        {:message (str "Set Code Failed")
         :data    all
         :status  0}))))

(defn refresh-code [refresh-token]
  (let [req (client/request {:url         token-url
                             :method      :post
                             :form-params {"client_id"     (edn-in [:graph :client-id])
                                           "scope"         (edn-in [:graph :scope])
                                           "redirect_uri"  redirect-url
                                           "grant_type"    "refresh_token"
                                           "client_secret" (edn-in [:graph :secret])
                                           "refresh_token" refresh-token}})
        {:keys [status body] :as all} @req]
    (if (= status 200)
      (let [{:keys [access_token refresh_token]} (json/parse-string body true)]
        (set-cache access_token refresh_token)
        (log/info "[todo-set-code] done set token by refresh-code to cache."))
      (log/warn "[todo-set-code] todo server call refresh return error: " all))))

;;;;;;;;;; TO_DO SERVICE ;;;;;;;;

(defn list-task
  "根据 TODO 列表 ID 和访问 Token 获取此列表下所有的任务"
  [{:keys [displayName id]} access-token]
  (try
    (let [req (client/request {:url     (format task-in-list-url id)
                               :method  :get
                               :headers {"Authorization" (str "Bearer " access-token)}
                               :timeout (edn-in [:graph :timeout-ms])})
          {:keys [status body] :as all} @req]
      (if (= status 200)
        (let [{data :value} (json/parse-string body true)
              ;_ (when (= displayName "\uD83C\uDFAF 技术") (println data))
              data (mapv #(assoc
                            (select-keys % [:title :status :lastModifiedDateTime
                                            :createdDateTime :importance
                                            :id :isReminderOn :completedDateTime
                                            :dueDateTime])
                            :listInfo {:name displayName :id id}) data)]
          data)
        (do (log/warn "[todo-list-task] error because req failed: " all)
            [])))
    (catch Exception e
      (log/warn "[todo-list-task] error because method failed: " (.getMessage e))
      [])))

(defn list-todo
  "根据访问 TOKEN 获取此用户的所有列表，并且对每个列表获取任务"
  [access-token]
  (try
    (let [req (client/request {:url     list-url
                               :method  :get
                               :headers {"Authorization" (str "Bearer " access-token)}
                               :timeout (edn-in [:graph :timeout-ms])})
          {:keys [status body] :as all} @req]
      (if (= status 200)
        (let [{data :value} (json/parse-string body true)
              data (mapv #(select-keys % [:displayName :id]) data)]
          (flatten (mapv #(list-task % access-token) data)))
        (do (log/warn "[todo-list-todo] failed because req failed: " all)
            [])))
    (catch Exception e
      (log/warn (str "[todo-list-todo] failed because method failed: " (.getMessage e)))
      [])))

(defn merge-todo-to-db
  "将同步的数据整合到数据库，其中有很多重复数据，要小心处理"
  [tasks]
  (doseq [{:keys [title id] :as all} tasks]
    (try
      (let [res (db/insert-to-do {:id id :title title :info all})])
      ;_ (println res)

      (catch Exception e
        (log/error "[todo-merge] insert into db error: " (.getMessage e))))))

(defn todo-sync-delete-id
  "找到需要删除的数据库 Task ID 并执行删除"
  [tasks-server]
  (try
    (let [id-title-db (db/to-do-modify-in-2-days)
          ids-server (set (mapv #(:id %) tasks-server))
          ids-db (set (mapv #(:id %) id-title-db))
          id-need-delete-list (set/difference ids-db ids-server)
          _ (log/info "[todo-sync] need delete outdated: " id-need-delete-list)]
      (doseq [id-need-del id-need-delete-list]
        (db/delete-by-id {:id id-need-del})))
    (catch Exception e
      (log/error "[todo-sync] try fetch and delete outdated data failed: " (.getMessage e)))))

(defn todo-sync-server-to-db
  "将 MS Server 数据同步到数据库，并且删除最近 MS Server 中不存在，数据库存在的数据"
  [access-token]
  (let [tasks (list-todo access-token)]
    (merge-todo-to-db tasks)
    (when-not (empty? tasks) (todo-sync-delete-id tasks))))

(defn todo-sync-routine []
  (let [{:keys [access-token refresh-token]} (fetch-cache)]
    (cond (and (nil? access-token) (nil? refresh-token))
          (do
            (slack/notify "TODO Token 过期！" "SERVER")
            (log/info "[todo-sync] no at and rt in cache, pls login mazhangjing.com/todologin to set code"))
          (nil? access-token)
          (do (log/info "[todo-sync] not find at, use rt to refresh at")
              (refresh-code refresh-token))
          :else (todo-sync-server-to-db access-token))))

(defn backend-todo-service []
  (while true
    (try
      (let [sleep-sec (* 60 5)]
        (future (news/news-push-routine))
        (try
          (log/debug "[todo-service] starting sync with ms-server...")
          (todo-sync-routine)
          (log/debug "[todo-service] end sync with ms-server, try to sleep sec: " sleep-sec)
          (catch Exception e
            (log/info "[todo-service] sync with ms-server failed: " (.getMessage e))))
        (Thread/sleep (* 1000 sleep-sec)))
      (catch Exception e
        (log/info "[todo-service] todo-service routine failed: " (.getMessage e))))))

;;;;;;;;;; TICKET SERVICE ;;;;;;;;

(defn parse-rail-data
  "提取 12306 邮件的车票信息"
  [input id]
  (let [[_ orderNo year month day hour minute start end trainNo siteNo checkNo]
        (re-find #"订单号码\W([A-Z0-9]+)\W.*?(\d{4})年(\d{2})月(\d{2})日(\d{2}):(\d{2})开，(.*?)站-(.*?站)，(.*?)次列车,(.*?)，.*?检票口(.*?)，"
                 (str/replace (or input "") "\r\n" ""))]
    (if (or (nil? orderNo) (nil? year) (nil? month) (nil? day) (nil? hour) (nil? minute) (nil? start) (nil? end) (nil? trainNo))
      (let [[_ orderNo year month day hour minute start end trainNo siteNo]
            (re-find #"订单号码\W([A-Z0-9]+)\W.*?(\d{4})年(\d{2})月(\d{2})日(\d{2}):(\d{2})开，(.*?)站-(.*?站)，(.*?)次列车,(.*?)，"
                     (str/replace (or input "") "\r\n" ""))]
        (if (or (nil? orderNo) (nil? year) (nil? month) (nil? day) (nil? hour) (nil? minute) (nil? start) (nil? end) (nil? trainNo))
          {:originData   input
           :fallbackData (if (> (count input) 150) (.substring input 103 150) input)
           :id           id})
        {:orderNo    orderNo
         :date       (LocalDateTime/of (Integer/parseInt year) (Integer/parseInt month) (Integer/parseInt day)
                                       (Integer/parseInt hour) (Integer/parseInt minute))
         :start      start
         :end        end
         :trainNo    trainNo
         :siteNo     siteNo
         :originData input
         :id         id})
      {:orderNo    orderNo
       :date       (LocalDateTime/of (Integer/parseInt year) (Integer/parseInt month) (Integer/parseInt day)
                                     (Integer/parseInt hour) (Integer/parseInt minute))
       :start      start
       :end        end
       :trainNo    trainNo
       :siteNo     siteNo
       :checkNo    checkNo
       :originData input
       :id         id})))

(defn parsed-rail-data->str
  "将结构化信息转换为一句话信息"
  [{:keys [id orderNo date start end trainNo siteNo checkNo originData fallbackData]}]
  (if fallbackData
    fallbackData
    (if checkNo
      (format "%s %s->%s %s %s 在 %s 检票。" (DateTimeFormatter/ofPattern "HH:ss" date)
              start end
              trainNo siteNo checkNo)
      (format "%s %s->%s %s %s。" (DateTimeFormatter/ofPattern "HH:ss" date)
              start end
              trainNo siteNo))))

(defn list-rail-tickets
  "获取最近的车票信息"
  [access-token]
  (try
    (let [req (client/request {:url     outlook-rail-check-url
                               :method  :get
                               :headers {"Authorization" (str "Bearer " access-token)}
                               :timeout (edn-in [:graph :timeout-ms])})
          {:keys [status body] :as all} @req]
      (if (= status 200)
        (let [{data :value} (json/parse-string body true)
              ;:value -> :id, :createDateTime, :sentDateTime, :subject, :bodyPreview
              ;尊敬的 x先生：您好！您于2023年xx月xx日在中国铁路客户服务中心网站(12306.cn) 成功购买了1张车票，
              ;票款共计xxx.00元，订单号码 E72104xxxx 。 所购车票信息如下：1.马章竞，20xx年0x月0x日xx:xx开，
              ;xx站-xx站，G123次列车,6车xxA号，二等座，成人票，票价xxx.0元，检票口xxB，电子客票。
              ;:isRead, :isDraft, :webLink, :body {:contentType, :content},
              ;:sender {:emailAddress {:name, :address}}
              ;:from {:emailAddress {:name, :address}}
              data (mapv #(parse-rail-data (:bodyPreview %) (:id %)) data)]
          data)
        (do (log/warn "[12306] error because req failed: " all)
            [])))
    (catch Exception e
      (log/warn "[12306] error because method failed: " (.getMessage e))
      [])))

(defn ticket-mail-check-routine
  ;TODO Token 和 RefreshToken 设置 Mail.read 权限
  ;TODO 调用创建待办事项接口创建事项
  ;https://learn.microsoft.com/zh-cn/graph/api/todotasklist-post-tasks?view=graph-rest-1.0&tabs=http
  ;1. 获取所有列表，找到列表id
  ;https://graph.microsoft.com/v1.0/me/to,do/lists
  ;:value [{:id :displayName}]
  ;2. 获取列表最近事项，查看是否存在
  ;GET /me/to,do/lists/{todoTaskListId}/tasks
  ;:value [{:title :id}]
  ;3. 如果不存在，则创建
  ;POST /me/to,do/lists/{todoTaskListId}/tasks
  ;:title :categories ["Important"] :importance [:low :normal :high] :status
  ;:completedDateTime :dueDateTime :createDateTime :lastModifiedDateTime :bodyLastModifiedDateTime :reminderDateTime
  ;recurrence :id
  []
  (let [{:keys [access-token refresh-token]} (fetch-cache)]
    (cond (and (nil? access-token) (nil? refresh-token))
          (do
            (slack/notify "GRAPH Token 过期！" "SERVER")
            (log/info "[mail-sync] no at and rt in cache, pls login mazhangjing.com/todologin to set code"))
          (nil? access-token)
          (do (log/info "[mail-sync] not find at, use rt to refresh at")
              (refresh-code refresh-token))
          :else
          (let [tickets (list-rail-tickets access-token)]
            (when-not (empty? tickets)
              (println "[mail-sync] saving tickets count " (count tickets))
              (ticket/handle-set-tickets tickets))))))

(defn backend-ticket-mail-service []
  (while true
    (try
      (let [sleep-sec (* 60 60)]
        (try
          (log/debug "[graph-service] starting check mail tickets with ms-server...")
          (ticket-mail-check-routine)
          (log/debug "[graph-service] end check mail tickets with ms-server, try to sleep sec: " sleep-sec)
          (catch Exception e
            (log/info "[graph-service] sync with check mail tickets with ms-server failed: " (.getMessage e))))
        (Thread/sleep (* 1000 sleep-sec)))
      (catch Exception e
        (log/info "[graph-service] mail-tickets-service routine failed: " (.getMessage e))))))

;;;;;;;;;;; INTERNAL API ;;;;;;;;;

(defn sort-todo
  "对待办事项按照如下规则进行排序：是否完成（未完成优先）、列表、创建时间（较晚的优先）"
  [todo-list]
  (sort (fn [{s1 :status l1 :list c1 :create_at :as a1}
             {s2 :status l2 :list c2 :create_at :as a2}]
          (cond (= s1 s2)
                (if (= l1 l2) (* -1 (compare c1 c2)) (compare l1 l2))
                (= "completed" s1) 100
                (= "completed" s2) -100
                :else (compare a1 a2)))
        todo-list))

(defn handle-set-code [{:keys [code]}]
  (set-code code))

(defn handle-focus-sync-todo []
  (todo-sync-routine)
  {:message "Sync Done." :status 1})

(defn handle-focus-sync-mail-tickets []
  (ticket-mail-check-routine)
  {:message "Sync Done." :status 1})

(defn handle-today
  "返回今日任务，Go API 兼容：{:startCount :tasks []}
  这里 startCount 表示优先级为 high 且状态为 notStarted 的
  focus 是尽力而为的服务，即如果无法获取到 access-token 则不执行同步，
  需要手动先执行一遍登录或者 refresh-token 刷新，写入 access-token 才能在下次调用时生效。

  这里使用 high + notStart 的原因是，Exchange 没有 Today 的逻辑，因此通过
  点亮星星，即 high importance 来表示 Today 的 TODO。因此安排的非今天任务要摘掉星星，
  安排给今天的任务要加上星星才能完成此逻辑。"
  [{:keys [focus showCompleted]}]
  (try
    (when focus
      (log/info "[today] focusing to handle-today!!")
      (let [{:keys [access-token]} (fetch-cache)]
        (if-not (nil? access-token)
          (todo-sync-server-to-db access-token)
          (log/warn "[todo-today] need focus but miss access-token, "
                    "may not enable by todologin or not refresh token frequently."))))
    (let [all (db/to-do-recent-day-2 {:day 15})
          ;recent (filterv #(and (= (:importance %) "high") (= (:status %) "notStarted")) all)
          ;使用新计算方法：不管是否 high importance，只要 due or finish or create 是今天就算
          ;recent-start-and-not-start (filterv #(and (= (:importance %) "high")) all)
          today (LocalDate/now)
          today-todo (filterv #(and (:time %)
                                    (.isEqual today (:time %))
                                    (if showCompleted true (= (:status %) "notStarted"))
                                    (= (:importance %) "high")) all)]
      {:starCount (count today-todo)
       :tasks     today-todo})
    (catch Exception e
      {:starCount -1
       :tasks     []
       :message   (str "获取 Today 消息失败：" (.getMessage e))})))

(defn handle-recent
  "返回最近 n 天的 TODO 待办事项，按照天数进行分组"
  [{day :day :or {day 7}}]
  (let [data (db/to-do-recent-day-2 {:day day})]
    (let [origin (group-by #(str (:time %)) data)
          ;{"2022-10-10" [{:modified_at :time :finish_at :title :list
          ; :status :due_at :create_at :importance}]}
          sorted-data (into {}
                            (mapv (fn [[date-str todo-list]]
                                    [date-str (sort-todo todo-list)])
                                  origin))]
      sorted-data)))

(defn handle-work-today
  "获取今日的工作事项"
  []
  (try
    {:message "Success"
     :status  1
     :data
     (let [recent-items (db/to-do-recent-day-2 {:day 2})
           today (LocalDate/now)
           today-work-items (filterv (fn [{:keys [time list]}]
                                       (and (.isEqual today time)
                                            (str/includes? list "工作")))
                                     recent-items)
           with-time-items (mapv (fn [item]
                                   (let [title (or (:title item) "")
                                         [_ hour] (re-find #"\^(\d)h" title)]
                                     {:title title :hour (when hour (Integer/parseInt hour))}))
                                 today-work-items)
           ;空白、一个空hour（填充）、一个非空hour（异常）、两个空hour（填充）、三个空hour（填充分配）
           ;三个hour两个空（剩余分配）
           ;with-time-items [{:title "1" :hour nil} {:title "2" :hour nil} {:title "3" :hour 3}]
           all-hour-today (reduce (fn [agg item]
                                    (+ agg (or (:hour item) 0)))
                                  0 with-time-items)
           rest-hour (- 8 all-hour-today)
           rest-items (filterv #(nil? (:hour %)) with-time-items)]
       #_(println with-time-items all-hour-today rest-hour)
       (if (and (> rest-hour 0) (> (count rest-items) 0))
         (let [rest-count (count rest-items)
               each-item-need-carry (unchecked-divide-int rest-hour rest-count)
               the-last-item-need (if (= (mod rest-hour rest-count) 0)
                                    each-item-need-carry
                                    (- rest-hour (* (- rest-count 1) each-item-need-carry)))
               non-last-items (take (- rest-count 1) rest-items)
               last-item (last rest-items)]
           #_(println rest-items rest-count each-item-need-carry
                      the-last-item-need non-last-items last-item)
           (let [fixed-rest-items
                 (conj (mapv (fn [item] (assoc item :hour each-item-need-carry))
                             non-last-items)
                       (assoc last-item :hour the-last-item-need))]
             (into (filterv #(not (nil? (:hour %))) with-time-items)
                   fixed-rest-items)))
         with-time-items))}
    (catch Exception e
      {:message (str "Error:" (.getMessage e))
       :status  -1})))

(defn handle-week-static
  "返回本周的 TODO 待办事项，格式：{:2022-03-01 {:finished 3 :total 4}}"
  []
  (let [data (handle-recent 8)
        days-date (tool/all-week-day)]
    (reduce (fn [acc one-date]
              (let [collect (get data (str one-date) [])]
                (assoc acc
                  (-> one-date str keyword)
                  {:finished (count (filter #(= (:status %) "completed") collect))
                   :total    (count collect)})))
            {} days-date)))

(defn handle-list
  "获取倒序排列的最近 TODO 任务，限制某个列表和某个时期"
  [{:keys [day listName]
    :or   {day 7}}]
  (if (nil? listName)
    {:message "没有传入列表名称"
     :status  0}
    (let [data-recent (db/to-do-recent-day-2 {:day day})
          final-data (filter #(= (:list %) listName) data-recent)
          with-due-full (map (fn [{:keys [due_at create_at finish_at] :as all}]
                               (cond due_at all
                                     finish_at (assoc all :due_at finish_at)
                                     create_at (assoc all :due_at create_at)
                                     :else all)) final-data)
          final-data (sort (fn [x y]
                             (let [dx (:due_at x) dy (:due_at y)
                                   cx (:create_at x) cy (:create_at y)]
                               (if (= dx dy) (compare dy dx)
                                             (compare cy cx))))
                           with-due-full)]
      {:message "获取成功"
       :data    final-data
       :status  1})))

(comment
  (user/start)
  (clojure.pprint/pprint @cache)
  (user/create-migration "todo")
  (user/migrate)
  (cyberme.db.core/bind)
  (db/all-to-do)
  (db/to-do-recent-day-2 {:day 7})
  (in-ns 'cyberme.db.core)
  (conman/bind-connection *db* "sql/queries.sql" "sql/goods.sql" "sql/cyber.sql")
  (db/insert-to-do {:id "WR" :title "HELLO A" :info {:a "HELLO"}})
  (db/to-do-recent-day-2 {:day 15})
  (take 10 (db/to-do-all))
  (take 10 (db/to-do-modify-in-2-days)))
