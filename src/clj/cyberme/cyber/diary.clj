(ns cyberme.cyber.diary
  (:require [cyberme.db.core :as db]
            [clojure.tools.logging :as log]
            [next.jdbc :as jdbc])
  (:import
    (java.time.format DateTimeFormatter)
    (java.time LocalDate)))

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

(defn handle-recent-diaries
  "获取最近的日记"
  [_]
  (try
    {:message "获取成功"
     :data    (db/all-diary)
     :status  1}
    (catch Exception e
      {:message (str "获取最近的日记失败" (.getMessage e)) :status 0})))

(defn handle-diary-by-id
  "获取某一日记"
  [{:keys [id]}]
  (try
    (if-let [data (db/diary-by-id {:id id})]
      {:message "获取成功"
       :data    data
       :status  1}
      {:message (str "获取失败，没有找到 id 为 " id " 的日记。")
       :status  0})
    (catch Exception e
      {:message (str "获取日记 #" id " 失败" (.getMessage e))
       :status  0})))

(defn handle-diary-by-day
  "获取某一天的日记，天数使用 2022-03-01 格式传入"
  [{:keys [day-str]}]
  (try
    (let [day-inst (LocalDate/parse
                     day-str
                     (DateTimeFormatter/ISO_LOCAL_DATE))
          data (db/diaries-by-day {:day day-inst})]
      {:message "获取成功" :data data :status 1})
    (catch Exception e
      {:message (str "获取日记 @" day-str " 失败" (.getMessage e))
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
            insert-diary-action (db/insert-diary t
                                                 {:title   title
                                                  :content content
                                                  :info    info})]
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
            update-action (db/update-diary t {:title   title
                                              :content content
                                              :info    info
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