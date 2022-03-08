(ns cyberme.cyber.todo
  (:require [org.httpkit.client :as client]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [cyberme.db.core :as db]
            [clojure.set :as set])
  (:import (java.time LocalDateTime)))

;访问 mazhangjing.com/todologin 登录微软账户，然后其回调 mazhangjing.com/todocheck
;跳转到 ip/t odo/setcode?code=xxxx，这里保存 code 参数并触发更新缓存操作：/t odo/today。
(def client-id "f0272f58-462f-4ee9-a404-be8bf2e64379")
(def scope "tasks.readWrite,offline_access")
(def secret "DmjH10wqCmRIT4mIT~14W5k5GviOw-z.l~")
(def token-url "https://login.microsoftonline.com/common/oauth2/v2.0/token")
(def redirect-url "https://mazhangjing.com/todocheck")
(def list-url "https://graph.microsoft.com/v1.0/me/todo/lists")
(def todo-timeout-ms 20000)
(def task-in-list-url "https://graph.microsoft.com/v1.0/me/todo/lists/%s/tasks")

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
        at-out? (or (nil? at-expired) (.isBefore at-expired now))
        rt-out? (or (nil? rt-expired) (.isBefore rt-expired now))]
    {:access-token  (if at-out? nil access-token)
     :refresh-token (if rt-out? nil refresh-token)}))

(defn set-code [code]
  (let [req (client/request {:url         token-url
                             :method      :post
                             :form-params {"client_id"     client-id
                                           "scope"         scope
                                           "code"          code
                                           "redirect_uri"  redirect-url
                                           "grant_type"    "authorization_code"
                                           "client_secret" secret}})
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
                             :form-params {"client_id"     client-id
                                           "scope"         scope
                                           "redirect_uri"  redirect-url
                                           "grant_type"    "refresh_token"
                                           "client_secret" secret
                                           "refresh_token" refresh-token}})
        {:keys [status body] :as all} @req]
    (if (= status 200)
      (let [{:keys [access_token refresh_token]} (json/parse-string body true)]
        (set-cache access_token refresh_token)
        (log/info "[todo-set-code] done set token by refresh-code to cache."))
      (log/warn "[todo-set-code] todo server call refresh return error: " all))))

(defn list-task [{:keys [displayName id]} access-token]
  "根据 TODO 列表 ID 和访问 Token 获取此列表下所有的任务"
  (try
    (let [req (client/request {:url     (format task-in-list-url id)
                               :method  :get
                               :headers {"Authorization" (str "Bearer " access-token)}
                               :timeout todo-timeout-ms})
          {:keys [status body] :as all} @req]
      (if (= status 200)
        (let [{data :value} (json/parse-string body true)
              data (mapv #(assoc
                            (select-keys % [:title :status :lastModifiedDateTime
                                            :createdDateTime :importance
                                            :id :isReminderOn :completedDateTime])
                            :listInfo {:name displayName :id id}) data)]
          data)
        (do (log/warn "[todo-list-task] error because req failed: " all)
            [])))
    (catch Exception e
      (log/warn "[todo-list-task] error because method failed: " (.getMessage e))
      [])))

(defn list-todo [access-token]
  "根据访问 TOKEN 获取此用户的所有列表，并且对每个列表获取任务"
  (try
    (let [req (client/request {:url     list-url
                               :method  :get
                               :headers {"Authorization" (str "Bearer " access-token)}
                               :timeout todo-timeout-ms})
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

(defn merge-to-db [tasks]
  "将同步的数据整合到数据库，其中有很多重复数据，要小心处理"
  (doseq [{:keys [title id] :as all} tasks]
    (try
      (let [res (db/insert-to-do {:id id :title title :info all})
            ;_ (println res)
            ])
      (catch Exception e
        (log/error "[todo-merge] insert into db error: " (.getMessage e))))))

(defn sync-delete-id [tasks-server]
  "找到需要删除的数据库 Task ID 并执行删除"
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

(defn sync-server-to-db [access-token]
  "将 MS Server 数据同步到数据库，并且删除最近 MS Server 中不存在，数据库存在的数据"
  (let [tasks (list-todo access-token)]
    (merge-to-db tasks)
    (when-not (empty? tasks) sync-delete-id tasks)))

(defn todo-sync-routine []
  (let [{:keys [access-token refresh-token]} (fetch-cache)]
    (cond (and (nil? access-token) (nil? refresh-token))
          (do (log/info "[todo-sync] no at and rt in cache，pls login mazhangjing.com/todologin to set code"))
          (nil? access-token)
          (do (log/info "[todo-sync] not find at，use rt to refresh at")
              (refresh-code refresh-token))
          :else (sync-server-to-db access-token))))

(defn handle-set-code [{:keys [code]}]
  (set-code code))

(defn handle-focus-sync []
  (todo-sync-routine)
  {:message "Sync Done."})

(defn handle-today [{:keys [focus]}]
  "返回今日任务，Go API 兼容：{:startCount :tasks []}
  这里 startCount 表示优先级为 high 且状态为 notStarted 的
  focus 是尽力而为的服务，即如果无法获取到 access-token 则不执行同步，
  需要手动先执行一遍登录或者 refresh-token 刷新
  写入 access-token 才能在下次调用时生效"
  (try
    (when focus
      (let [{:keys [access-token]} (fetch-cache)]
        (if-not (nil? access-token)
          (sync-server-to-db access-token)
          (log/warn "[todo-today] need focus but miss access-token, "
                    "may not enable by todologin or not refresh token frequently."))))
    (let [all (db/to-do-recent-day {:day 15})
          recent (filterv #(and (= (:importance %) "high")
                                (= (:status %) "notStarted")) all)]
      {:starCount (count recent)
       :tasks recent})
    (catch Exception e
      {:starCount -1
       :tasks []
       :message (str "获取 Today 消息失败：" (.getMessage e))})))

(comment
  (user/start)
  (clojure.pprint/pprint @cache)
  (user/create-migration "todo")
  (user/migrate)
  (cyberme.db.core/bind)
  (db/all-to-do)
  (in-ns 'cyberme.db.core)
  (conman/bind-connection *db* "sql/queries.sql" "sql/goods.sql" "sql/cyber.sql")
  (db/insert-to-do {:id "WR" :title "HELLO A" :info {:a "HELLO"}})
  (db/to-do-recent-day {:day 15})
  (take 10 (db/to-do-all))
  (take 10 (db/to-do-modify-in-2-days))
  )