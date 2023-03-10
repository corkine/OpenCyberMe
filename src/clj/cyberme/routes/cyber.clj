(ns cyberme.routes.cyber
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [cyberme.auth :as auth]
    [cyberme.client.ios :as ios]
    [cyberme.cyber.book :as book]
    [cyberme.cyber.clean :as clean]
    [cyberme.cyber.dashboard :as dashboard]
    [cyberme.cyber.diary :as diary]
    [cyberme.cyber.express :as express]
    [cyberme.cyber.fitness :as fitness]
    [cyberme.cyber.goal :as goal]
    [cyberme.cyber.inspur :as inspur]
    [cyberme.cyber.note :as note]
    [cyberme.cyber.psych :as psych]
    [cyberme.cyber.slack :as slack]
    [cyberme.cyber.task :as task]
    [cyberme.cyber.graph :as graph]
    [cyberme.info.ticket :as ticket]
    [cyberme.cyber.track :as track]
    [cyberme.cyber.week-plan :as week]
    [cyberme.media.mini4k :as mini4k]
    [cyberme.media.disk :as disk]
    [cyberme.media.yyets :as yyets]
    [cyberme.auto.hcm :as auto]
    [cyberme.middleware :as middleware]
    [cyberme.middleware.formats :as formats]
    [reitit.coercion.spec :as spec-coercion]
    [reitit.ring.coercion :as coercion]
    [spec-tools.data-spec :as ds]
    [reitit.ring.middleware.multipart :as multipart]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.parameters :as parameters]
    [reitit.swagger :as swagger]
    [reitit.swagger-ui :as swagger-ui]
    [ring.util.http-response :refer :all]
    [ring.util.response :as hr]
    [promesa.core :as p]
    [promesa.exec.csp :as sp])
  (:import (java.time LocalDate)))

(defn- with-token
  "基于 data-spec 将格式
  :opt [:k1 string? :k2 boolean?]
  :req [:k3 map? :k4 int?]
  转换为
  {(ds/opt :k1) string?
   (ds/opt :k2) boolean?
   :k3 map?
   :k4 int?
   (ds/opt :user) string?
   (ds/opt :secret) string?}"
  [& args]
  (let [{:keys [opt req] :or {opt [] req []}} (apply hash-map args)]
    (merge {(ds/opt :user) string? (ds/opt :secret) string?}
           (into {} (map (fn [[k v]] [(ds/opt k) v]) (apply hash-map (vec opt))))
           (apply hash-map req))))

(defn- with-
  "基于 data-spec 将格式
  :opt [:k1 string? :k2 boolean?]
  :req [:k3 map? :k4 int?]
  转换为
  {(ds/opt :k1) string?
   (ds/opt :k2) boolean?
   :k3 map?
   :k4 int?}"
  [& args]
  (let [{:keys [opt req] :or {opt [] req []}} (apply hash-map args)]
    (merge (into {} (map (fn [[k v]] [(ds/opt k) v]) (apply hash-map (vec opt))))
           (apply hash-map req))))

(defn- not-impl [_]
  (hr/content-type (hr/not-found "没有实现。") "text/plain"))

(defn- todo [_]
  (hr/content-type (hr/not-found "正在施工。") "text/plain"))

(def basic-route
  ["/cyber"
   {:coercion   spec-coercion/coercion
    :muuntaja   formats/instance
    :swagger    {:id ::cyber}
    :middleware [
                 auth/wrap-basic-auth
                 ;; query-params & form-params
                 parameters/parameters-middleware
                 ;; content-negotiation
                 muuntaja/format-negotiate-middleware
                 ;; encoding response body
                 muuntaja/format-response-middleware
                 ;; exception handling
                 coercion/coerce-exceptions-middleware
                 ;; decoding request body
                 muuntaja/format-request-middleware
                 ;; coercing response bodys
                 coercion/coerce-response-middleware
                 ;; coercing request parameters
                 coercion/coerce-request-middleware
                 ;; multipart
                 multipart/multipart-middleware
                 auth/wrap-logged
                 middleware/wrap-as-async]}

   ;; swagger documentation
   ["" {:no-doc  true
        :swagger {:info {:title       "cyberme-api"
                         :description "https://mazhangjing.com"}}}

    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]

    ["/api-docs/*"
     {:get (swagger-ui/create-swagger-ui-handler
             {:url    "/cyber/swagger.json"
              :config {:validator-url nil}})}]]])

(def to-do-route
  ["/todo"
   {:tags #{"微软待办"}}
   ["/checkVirtual"
    {:get (fn [_]
            (sp/go
              {:status  200
               :headers {"content-type" "text/plain"}
               :body    (format "now thread is %s" (Thread/currentThread))}))}]
   ["/testSleep0"
    {:get (fn [_]
            (log/info "sleep1")
            (Thread/sleep 1000)
            {:status  200
             :headers {"content-type" "text/plain"}
             :body    (format "now thread is %s" (Thread/currentThread))})}]
   ["/testSleep1"
    {:get (fn [_]
            (sp/go
              (.setName (Thread/currentThread) "sleep1")
              (log/info "sleep1")
              (Thread/sleep 1000)
              {:status  200
               :headers {"content-type" "text/plain"}
               :body    (format "now thread is %s" (Thread/currentThread))}))}]
   ["/testSleep2"
    {:get (fn [_ resp _]
            (sp/go
              (.setName (Thread/currentThread) "sleep2")
              (log/info "sleep2")
              (Thread/sleep 1000)
              (resp {:status  200
                     :headers {"content-type" "text/plain"}
                     :body    (format "now thread is %s" (Thread/currentThread))})))}]
   ["/testToday"
    {:get {:parameters  {:query (with-token :opt [:focus boolean? :showCompleted boolean?])}
           :handler     (fn [{{query :query} :parameters}]
                          (sp/go (hr/response (graph/handle-today query))))}}]
   ["/setcode"
    {:get {:summary     "登录并保存 XToken"
           :description "不应该直接调用此接口，而应该使用 mazhangjing.com/todologin 来
            登录并且回调此接口并设置 AccessToken 和 RefreshToken。"
           :parameters  {:query (with-token :opt [:code string?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (graph/handle-set-code query)))}}]
   ["/sync"
    {:get {:summary     "强制同步数据"
           :description "不应该直接调用此接口，此接口等同于内部线程自行同步更新方法调用。"
           :parameters  {:query (with-token)}
           :handler     (fn [{{_ :query} :parameters}]
                          (hr/response (graph/handle-focus-sync-todo)))}}]
   ["/today"
    {:get {:summary     "Microsoft TODO 服务"
           :description "如果使用 focus，则始终先去 MS Server 获取数据并保存到数据库
            然后从数据库获取数据，速度可能比较慢，且如果恰好 access-token 过期（如果内部线程
            同步速度在 3600s 之内，则完全不会发生），那么这一次
            focus 将仅使用 refresh-token 更新 access-token 而不更新数据库，下一次 focus 或者
            内部线程同步后才能获取正确数据。如果不使用 focus，则使用获取数据库数据，其依赖于内部
            线程和 MS Server 的同步来保持更新，速度很快。

            create_at 是 localDateTime，finish_at 和 due_at 精确到 localDate，due_at 使用
            00:00，而 finish_at 使用 08:00。
            "
           :parameters  {:query (with-token :opt [:focus boolean? :showCompleted boolean?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (graph/handle-today query)))}}]
   ["/list"
    {:get {:summary     "获取某列表待办事项"
           :description "列表必填，最近天数不填默认为 7 天"
           :parameters  {:query (with-token :req [:listName string?] :opt [:day int?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (graph/handle-list query)))}}]

   ["/recent"
    {:get {:summary     "获取最近待办事项"
           :description "所有列表，结果按照天数分组，天数不填默认为 7 天"
           :parameters  {:query (with-token :opt [:day int?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (graph/handle-recent query)))}}]

   ["/work-today"
    {:get {:summary     "获取当日的工作事项"
           :description "获取当日的工作事项"
           :parameters  {:query (with-token)}
           :handler     (fn [_] (hr/response (graph/handle-work-today)))}}]])

(def tickets-route
  ["/ticket"
   {:tags #{"12306车票"}}
   ["/sync"
    {:get {:summary     "强制获取 GRAPH Mail 中的车票邮件"
           :description "不应该直接调用此接口，此接口等同于内部线程自行同步更新方法调用。"
           :parameters  {:query (with-token)}
           :handler     (fn [{{_ :query} :parameters}]
                          (hr/response (graph/handle-focus-sync-mail-tickets)))}}]
   ["/today"
    {:get {:summary     "获取今日车票"
           :description "获取当日的车票信息"
           :parameters  {:query (with-token :opt [:focus boolean?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (ticket/handle-fetch-today-tickets)))}}]
   ["/recent"
    {:get {:summary     "获取最近车票"
           :description "获取最近的车票信息"
           :parameters  {:query (with-token :opt [:limit int?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (ticket/handle-fetch-recent-tickets query)))}}]])

(def check-route
  ["/check"
   {:tags #{"工作相关"}}
   ["/hint"
    {:get {:summary     "当日生活提醒服务：HCM、健身、饮食和刷牙"
           :description "返回当日生活信息，比如 HCM 打卡，健身，饮食和刷牙情况"
           :parameters  {:query (with-token :opt [:token string?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (inspur/handle-serve-hint query)))}}]
   ["/hint_summary_todo"
    {:get {:summary     "当日生活提醒服务：HCM、健身、饮食和刷牙"
           :description "返回当日生活信息，比如 HCM 打卡，健身，饮食和刷牙情况，以及天气信息（来自缓存）
            此外包括 Summary API 信息和 TODO 信息，weather/id 为天气 ID"
           :parameters  {:query (with-token :opt [:token string? :kpi int?
                                                  :focus boolean? :id string?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (inspur/handle-serve-hint-summary query)))}}]
   ["/now"
    {:get {:summary     "获取当前打卡情况 (Pixel)"
           :description "仅供 PIXEL 使用的，打卡后通知 Slack 的内部方法，默认不使用缓存，但设置缓存。
           plainText 返回文本，而非 JSON 数据。
           useCache 总是使用缓存结果，不发送 HCM 请求。
           preferCacheSuccess 先检查缓存，缓存成功则直接返回，反之发送 HCM 请求。

           使用 useCache 导致任何打卡都最长延迟 HCM-INFO L2 缓存周期时间
           使用 preferCacheSuccess 能保证当天第一次打上班和下班卡无延迟，下班打多次卡除了第一次
           其他的会延迟最长一个 HCM-INFO L2 缓存周期时间。"
           :parameters  {:query (with-token :opt [:token string?
                                                  :plainText boolean?
                                                  :useCache boolean?
                                                  :preferCacheSuccess boolean?])}
           :handler     (fn [{{{:keys [plainText] :as query} :query} :parameters}]
                          (let [res (inspur/handle-serve-today query)]
                            (if plainText
                              (hr/content-type (hr/response (:message res)) "text/plain")
                              (hr/response res))))}}]
   ["/today"
    {:get {:summary     "HCM 每日信息统计"
           :description "获取今日打卡情况"
           :parameters  {:query (with-token :opt [:token string? :adjust int?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (inspur/handle-serve-day query)))}}]
   ["/thisWeek"
    {:get {:summary     "HCM 每周信息统计"
           :description "获取本周打卡情况"
           :parameters  {:query (with-token :opt [:token string? :adjust int?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (inspur/handle-serve-this-week query)))}}]

   ["/month_summary"
    {:get {:summary     "HCM 本月信息统计"
           :description "获取本月打卡、策略、休息等情况"
           :parameters  {:query (with-token)}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (inspur/handle-serve-month-summary query)))}}]

   ["/all_summary"
    {:get {:summary     "HCM 所有时间的信息统计"
           :description "获取本月打卡、策略、休息等情况"
           :parameters  {:query (with-token)}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response
                            (inspur/handle-serve-sometime-summary
                              (merge query
                                     {:date-list               (inspur/day-from (LocalDate/of 2021 06 01))
                                      :with-last-month-all-day true}))))}}]

   ["/summary"
    {:get {:summary     "HCM 所有信息统计"
           :description "获取 2021-06-01 日后所有打卡情况和打卡统计信息、加班信息"
           :parameters  {:query (with-token :opt [:token string?
                                                  :kpi int?
                                                  :use2MonthData boolean?
                                                  :useAllData boolean?
                                                  :showDetails boolean?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (inspur/handle-serve-summary query)))}}]

   ["/set_token"
    {:get {:summary     "写入默认 Token"
           :description "写入默认的 Token 信息"
           :parameters  {:query (with-token :req [:token string?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (inspur/handle-set-cache query)))}}]

   ["/set_token_auto"
    {:get {:summary     "自动写入 HCM Token"
           :description "写入默认的 Token 信息"
           :parameters  {:query (with-token)}
           :handler     (fn [_]
                          (hr/response (auto/handle-set-token)))}}]

   ["/overtime_bot_conf"
    {:get {:summary "加班机器人配置信息（废弃）" :handler not-impl}}]

   ["/overtime_check"
    {:get {:summary "今天加班检查（废弃）" :handler not-impl}}]

   ["/overtime_conf"
    {:get {:summary "加班配置信息（废弃）" :handler not-impl}}]

   ["/overtime_hint"
    {:get {:summary "今日加班一览（废弃）" :handler not-impl}}]

   ["/overtime_order"
    {:get {:summary "今日加班确认（废弃）" :handler not-impl}}]

   ["/overtime_week_plan"
    {:get {:summary "本周计划加班信息（废弃）" :handler not-impl}}]])

(def auto-route
  ["/auto"
   {:tags #{"工作相关"}}
   [""
    {:get {:summary     "上班状态自动检查 (Pixel)"
           :description "供 PIXEL 使用的内部接口，检查当前时间是否需要自动执行计划。
            如果检查时间在任一策略区间内，则返回 YES，反之返回 NO，出错返回一句话提示。
            如果当前时间也在策略区间内，则在返回前还持久化当前请求将其看做一次 600s 的任务。
            在返回前还要判断参数 mustInRange，其默认为 true，如果此标记 true 或空
            则只有当检查时间和当前时间都在任一策略区间内，才返回 YES。如果标记为 false，
            则仅检查时间在策略区间内即返回 YES。"
           :parameters  {:query (with-token :req [:needCheckAt string?]
                                            :opt [:mustInRange boolean?])}
           :handler     (fn [{{query :query} :parameters}]
                          (let [resp (inspur/handle-serve-auto query)
                                _ (log/info "[hcm-auto] response is " resp)]
                            (hr/content-type (hr/response resp) "text/plain")))}}]
   ["/info"
    {:get  {:summary     "最近上班状态条件"
            :description "返回最近上班状态标记, day 为最近天数"
            :parameters  {:query (with-token :opt [:day int?])}
            :handler     (fn [{{query :query} :parameters}]
                           (hr/response (inspur/handle-serve-list-auto query)))}
     :post {:summary     "最近上班状态添加"
            :description "添加某天的状态标记，其中 date 格式为 20220101，start 和 end 格式为 10:01-10:22"
            :parameters  {:query (with-token :req [:date string? :start string? :end string?])}
            :handler     (fn [{{query :query} :parameters}]
                           (hr/response (inspur/handle-serve-set-auto query)))}}]
   ["/:date/delete"
    {:post {:summary     "删除某个上班状态"
            :description "删除某天的状态标记，日期格式必须为 20220101"
            :parameters  {:path  {:date string?}
                          :query (with-token)}
            :handler     (fn [{{data :path} :parameters}]
                           (hr/response (inspur/handle-serve-delete-auto data)))}}]])

(def express-route
  ["/express"
   {:tags #{"快递追踪"}}
   ["/check"
    {:get {:summary     "快递信息查询"
           :description "传入 no 快递号以单次查询快递信息，type 为快递类型，默认不填为 AUTO"
           :parameters  {:query (with-token :req [:no string?] :opt [:type string?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (express/simple-check query)))}}]
   ["/track"
    {:get {:summary     "新增快递追踪"
           :description "追踪某一快递信息，调用后会立刻查询，且保存到数据库，如果快递尚未发货
            或者正在运输则进行自动追踪。type 为快递类型，默认为 AUTO，note 为通知时的快递别名。
            如果 rewriteIfExist 为 false，那么如果快递已经在数据库则不设置追踪，默认为 true
            则重置数据库数据为追踪状态。对于异常终止的快递，设置为 true 可强制开启追踪"
           :parameters  {:query (with-token :req [:no string?]
                                            :opt [:type string? :note string?
                                                  :rewriteIfExist boolean?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (express/simple-track query)))}}]

   ["/routine"
    {:get {:summary     "手动执行数据库快递追踪数据检查"
           :description "不应该直接调用，等同于内部接口周期性轮序追踪快递"
           :parameters  {:query (with-token)}
           :handler     (fn [_] (hr/response (express/track-routine)))}}]])

(def location-route
  ["/location"
   {:tags #{"快递追踪"}
    :get  {:summary     "鹰眼追踪"
           :description "上报 GPS 信息。"
           :parameters  {:query (with-token :req [:by string? :lo double? :la double?]
                                            :opt [:al double? :ve double? :ho double?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (track/handle-track query)))}}])

(def note-route
  ["/note"
   {:tags #{"笔记记录"}}
   [""
    {:get  {:summary    "便签信息查询"
            :parameters {:query (with-token :opt [:id int? :justContent boolean?
                                                  :quick boolean? :content string?])}
            :handler    (fn [{{query :query} :parameters}]
                          (let [just-content (:justContent query)
                                resp (hr/response (note/handle-fetch-note query))]
                            (if just-content (content-type resp "text/plain") resp)))}
     :post {:summary    "便签信息新建"
            :parameters {:query (with-token)
                         :body  (with- :req [:from string? :content string?]
                                       :opt [:id int? :liveSeconds int?])}
            :handler    (fn [{{body :body} :parameters}]
                          (hr/response (note/handle-add-note body)))}}]
   ["/last"
    {:get {:summary     "获取最后一条便签"
           :description "用于前端快捷获取最后一条便签，内容将被复制到 message 中。"
           :handler     (fn [_] (hr/response (note/handle-fetch-last-note)))}}]])

(def movie-route
  ["/movie"
   {:tags #{"电影电视"}}
   ["/"
    {:post {:summary    "添加电影电视跟踪"
            :parameters {:query (with-token :req [:name string? :url string?])}
            :handler    (fn [{{query :query} :parameters}]
                          (hr/response (mini4k/handle-add-movie query)))}
     :get  {:summary    "获取跟踪列表"
            :parameters {:query (with-token)}
            :handler    (fn [_] (hr/response (mini4k/handle-list-movie)))}}]
   ["/:id/delete"
    {:post {:summary    "删除此跟踪电影"
            :parameters {:path  {:id int?}
                         :query (with-token)}
            :handler    (fn [{{path :path} :parameters}]
                          (hr/response (mini4k/handle-delete-movie path)))}}]
   ["/yyets/search/:q"
    {:post {:summary    "搜索字幕组资源"
            :parameters {:path {:q any?}}
            :handler    (fn [{{path :path} :parameters}]
                          (hr/response (yyets/handle-search path)))}}]
   ["/yyets/resource/:id"
    {:get {:summary    "获取字幕组资源"
           :parameters {:path {:id any?}}
           :handler    (fn [{{path :path} :parameters}]
                         (hr/response (yyets/handle-resource (:id path))))}}]])

(def notice-route
  ["/notice"
   {:tags #{"消息通知"}
    :get  {:summary     "Slack 通知服务"
           :description "channel 为 PIXEL 则推送到 Pixel 通道，其余推送到服务器通道
            from 不提供默认为 Nobody"
           :parameters  {:query (with-token :req [:message string?]
                                            :opt [:channel string? :from string?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (slack/serve-notice query)))}}])

(def clean-route
  ["/clean"
   {:tags #{"清洁情况"}}
   ["/show"
    {:get {:summary     "展示清洁情况"
           :description ""
           :parameters  {:query (with-token)}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (clean/handle-clean-show query)))}}]
   ["/update"
    {:get {:summary     "更新清洁情况"
           :description "merge 用于和数据库数据整合，mt 早刷牙，nt 晚刷牙，mf 早用药，nf 晚用药，
            如果使用 merge，参数只有为 true 的才改写为 true，否者保持数据库记录。如果不适用 merge，
            未传递的参数看做 false 强行写入。
            可传递 yesterday true/false 指定昨天，传递 day yyyy-mm-dd 指定日期，解析出错使用今天"
           :parameters  {:query (with-token :opt [:merge boolean? :mt boolean? :nt boolean?
                                                  :mf boolean? :nf boolean?
                                                  :yesterday boolean? :day string?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (clean/handle-clean-update query)))}}]])

(def fitness-route
  ["/fitness"
   {:tags #{"健康数据"}}
   ["/iOSUpload"
    {:post {:summary     "上传健康样本（fitness 数据库，捷径）"
            :description "IOS 健康 App 上传最近样本（fitness 数据库，捷径）"
            :parameters  {:formData {:data any?}}
            :handler     (fn [{data :form-params}]
                           (hr/response (fitness/handle-shortcut-upload (get data "data"))))}}]
   ["/appUpload"
    {:post {:summary     "上传健康样本（HealthKit）"
            :description "IOS 健康 App 上传最近样本（HealthKit）"
            :parameters  {:body any?}
            :handler     (fn [{{data :body} :parameters}]
                           (hr/response (fitness/handle-ios-app-active-upload data)))}}]
   ["/data"
    {:get {:summary     "最近健康样本"
           :description "查看最近的健康样本"
           :parameters  {:query (with-token :opt [:category string?
                                                  :lastDays int? :limit int?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (fitness/handle-list query)))}}]
   ["/:id/delete"
    {:post {:summary     "删除健康样本"
            :description "删除健康样本"
            :parameters  {:query (with-token)
                          :path  {:id int?}}
            :handler     (fn [{{data :path} :parameters}]
                           (hr/response (fitness/handle-delete data)))}}]
   ["/:id/details"
    {:get {:summary     "查看健康样本详情"
           :description "查看健康样本详情"
           :parameters  {:query (with-token)
                         :path  {:id int?}}
           :handler     (fn [{{data :path} :parameters}]
                          (hr/response (fitness/handle-details data)))}}]])

(def blue-route
  ["/blue"
   {:tags #{"清洁情况"}}
   ["/update"
    {:get {:summary     "更新清洁情况 2"
           :description "blue=true/false 返回 updated 结果"
           :parameters  {:query (with-token :req [:blue boolean?] :opt [:day string?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (clean/handle-blue-set query)))}}]])

(def client-route
  ["/client"
   {:tags #{"Client API"}}
   ["/ios-summary"
    {:get {:summary     "iOS Summary API"
           :description "包括 Fitness、TODO、Blue、Clean、每周学习、每天日报 等信息"
           :parameters  {:query (with-token :opt [:day int?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (ios/handle-ios-dashboard query)))}}]

   ["/ios-widget"
    {:get {:summary     "iOS Widget API"
           :description "iOS 小组件信息，包括天气、TODO、打卡等"
           :parameters  {:query (with-token :opt [:day int?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (ios/handle-ios-widget query)))}}]

   ["/ios-fitness"
    {:post {:summary     "上传今日健康样本"
            :description "IOS 健康 App 上传最近样本（HealthKit）"
            :parameters  {:body any?}
            :handler     (fn [{{data :body} :parameters}]
                           (hr/response (ios/handle-ios-app-active-upload data)))}}]

   ["/ios-body-mass"
    {:post {:summary     "上传今日体重样本"
            :description "IOS 健康 App 上传最近体重样本（HealthKit）"
            :parameters  {:body any?}
            :handler     (fn [{{data :body} :parameters}]
                           (hr/response (ios/handle-ios-upload-body-mass data)))}}]])

(def dashboard-route
  ["/dashboard"
   {:tags #{"前端大屏"}}
   ["/summary"
    {:get {:summary     "获取当日综合信息"
           :description "包括 Fitness、TODO、Blue、Clean 等信息"
           :parameters  {:query (with-token :opt [:day int?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (dashboard/handle-dashboard query)))}}]

   ["/day-work"
    {:get  {:summary     "获取当日日报"
            :description "获取当日日报情况，如果是非工作日，则拦截数据库读写并返回“无需日报”信息。"
            :handler     (fn [_] (hr/response (diary/handle-day-work)))}
     :post {:summary     "更新当日日报"
            :description "更新当日日报情况"
            :parameters  {:body any?}
            :handler     (fn [{{data :body} :parameters}]
                           (hr/response (diary/handle-day-work-update data)))}}]

   ["/plant-week"
    {:get  {:summary     "获取本周浇花和每周学习情况"
            :description "获取本周浇花和每周学习情况: {:status [0 1 0 1 0 1 0] :learn :done/:not-done
            :week-plan [本周计划信息]}"
            :handler     (fn [_] (hr/response (diary/handle-plant-learn-week)))}
     :post {:summary     "更新本周浇花情况"
            :description "不管参数如何，如果今天已经浇花，则设置为未浇花，反之设置为已浇花，返回本周浇花情况"
            :parameters  {:body any?}
            :handler     (fn [{{data :body} :parameters}]
                           (hr/response (diary/handle-plant-week-update-today data)))}}]

   ["/learn-week"
    {:post {:summary     "更新本周学习情况"
            :description "更新本周学习情况，参数有四：start non-start end non-end 分别表示
            是否标记一项新学习、取消此新学习，结束此学习、取消结束此学习。每天最多一次学习。"
            :parameters  {:body any?}
            :handler     (fn [{{data :body} :parameters}]
                           (hr/response (diary/handle-set-today-learn data)))}}]

   ["/psych-data-upload"
    {:post {:summary     "实验数据上传"
            :description "上传实验数据，数据可能有重复。"
            :parameters  {:body any?}
            :handler     (fn [{{data :body} :parameters}]
                           (hr/response (psych/add-log data)))}}]

   ["/psych-data-download/:exp-id"
    {:get {:summary     "实验数据下载"
           :description "下载实验数据，数据去重。"
           :parameters  {:query (with-token :opt [:day int? :plain-text boolean?])
                         :path  {:exp-id string?}}
           :handler     (fn [{{query :query path :path} :parameters}]
                          (if-not (:plain-text query)
                            (hr/response (psych/recent-log (merge path query)))
                            (hr/content-type
                              (hr/response (psych/recent-log-plain (merge path query)))
                              "text/plain")))}}]])

(def diary-route
  [""
   ["/diaries"
    {:tags #{"我的日记"}
     :get  {:summary     "获取最近的所有日记"
            :description "日记包括 title content info create_at update_at id 信息
            其中 from to 表示开始和结束的条数，默认 from 从 0 开始，默认 to 为 100"
            :parameters  {:query (with-token :opt [:from int? :to int? :is-draft? boolean?])}
            :handler     (fn [{{query :query} :parameters auth :auth-info}]
                           (hr/response (diary/handle-diaries-limit (dissoc (merge query auth)
                                                                            :is-draft?))))}
     :post {:summary     "获取特定查询参数日记"
            :description "查询参数包括：from, to(用于分页),
            from-year, to-year, from-month, to-month, year, month(过滤查询)
            origin, search, tag(搜索原始关键字和搜索关键字)"
            :parameters  {:query (with-token)
                          :body  any?}
            :handler     (fn [{{body :body} :parameters auth :auth-info}]
                           (hr/response (diary/handle-diaries-query (dissoc (merge body auth)
                                                                            :is-draft?))))}}]
   ["/diaries-draft"
    {:tags #{"我的日记"}
     :post {:summary     "获取特定查询参数草稿日记"
            :description "查询参数包括：from, to(用于分页),
            from-year, to-year, from-month, to-month, year, month(过滤查询)
            origin, search, tag(搜索原始关键字和搜索关键字)"
            :parameters  {:query (with-token)
                          :body  any?}
            :handler     (fn [{{body :body} :parameters auth :auth-info}]
                           (hr/response (diary/handle-diaries-query (assoc (merge body auth)
                                                                      :is-draft? true))))}}]
   ["/diary-new"
    {:tags #{"我的日记"}
     :post {:summary     "新建日记"
            :description "日记包括 title content info 信息"
            :parameters  {:query (with-token)
                          :body  any?}
            :handler     (fn [{{body :body} :parameters}]
                           (hr/response (diary/handle-insert-diary body)))}}]
   ["/diary"
    {:tags #{"我的日记"}}
    ["/by-date/:date"
     {:get {:summary     "获取某一日记"
            :description "日记包括 title content info create_at update_at id 信息"
            :parameters  {:query (with-token)
                          :path  {:date string?}}
            :handler     (fn [{{path :path} :parameters auth :auth-info}]
                           (hr/response (diary/handle-diary-by-day (merge path auth))))}}]
    ["/by-id/:id"
     {:get  {:summary     "获取某一日记"
             :description "日记包括 title content info create_at update_at id 信息"
             :parameters  {:query (with-token)
                           :path  {:id int?}}
             :handler     (fn [{{path :path} :parameters auth :auth-info}]
                            (hr/response (diary/handle-diary-by-id (merge path auth))))}
      :post {:summary     "更新某一日记"
             :description "日记更新的包括 title content info id 信息"
             :parameters  {:query (with-token)
                           :path  {:id int?}
                           :body  any?}
             :handler     (fn [{{body :body path :path} :parameters}]
                            (hr/response (diary/handle-update-diary
                                           (merge path body))))}}]
    ["/by-id/:id/delete"
     {:post {:summary     "删除某一日记"
             :description "传入 id 删除"
             :parameters  {:query (with-token)
                           :path  {:id int?}}
             :handler     (fn [{{path :path} :parameters}]
                            (hr/response (diary/handle-diary-delete path)))}}]]])

(def task-route
  [""
   ["/task/:id/job"
    {:tags #{"分布式任务"}
     :get  {:summary     "获取下一个分布式任务"
            :description "获取下一个分布式任务"
            :parameters  {:path {:id string?} :query {:bot string?}}
            :handler     (fn [{{{id :id} :path {bot :bot} :query} :parameters}]
                           (hr/response (task/fetch-job id bot)))}
     :post {:summary     "上传一个分布式任务"
            :description "上传一个已完成的分布式任务"
            :parameters  {:body any? :path {:id string?}}
            :handler     (fn [{{:keys [body path]} :parameters}]
                           (hr/response (task/upload-job (:id path) body)))}}]])

(def week-plan-route
  ;"其中获取本周计划使用 API /dashboard/plant-week，其余 API 参见此处"
  ["/week-plan"
   {:tags #{"每周计划"}}
   ["/list-item"
    {:get {:summary     "列出本周计划项目"
           :description "列出本周计划项目"
           :parameters  {:query (with-token)}
           :handler     (fn [_]
                          (hr/response (week/handle-get-week-plan)))}}]
   ["/list-items"
    {:get {:summary     "列出指定时间范围的所有计划项目"
           :description "列出指定时间范围的所有计划项目，从本周起，向前 range-week 周, 数据格式 {:date :result}"
           :parameters  {:query (with-token :opt [:from int? :to int?])}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (week/handle-get-week-plan-range query)))}}]
   ["/add-item"
    {:post {:summary     "添加本周计划项目"
            :description "添加本周计划项目，需要传入至少 name, category,
            可选 description, progress, id，其中 category 为 learn/work/fitness/diet"
            :parameters  {:query (with-token)
                          :body  any?}
            :handler     (fn [{{body :body} :parameters}]
                           (hr/response (week/handle-add-week-plan-item body)))}}]
   ["/modify-item"
    {:post {:summary     "更新本周计划项目"
            :description "更新本周计划项目，需要传入 id，只能更新 name 和description, 可传入 date（可能为空）"
            :parameters  {:query (with-token)
                          :body  any?}
            :handler     (fn [{{body :body} :parameters}]
                           (hr/response (week/handle-modify-week-plan-item body)))}}]
   ["/delete-item/:item-id"
    {:post {:summary     "删除本周计划项目"
            :description "删除此项目和项目的每个记录，最后一条会同时删除记录行/本周计划"
            :parameters  {:query (with-token)
                          :path  {:item-id string?}}
            :handler     (fn [{{{:keys [item-id]} :path} :parameters}]
                           (hr/response (week/handle-delete-week-plan-item item-id)))}}]
   ["/update-item/:item-id/add-log"
    {:post {:summary     "更新本周计划项目：添加记录"
            :description "更新本周计划项目：添加记录。
           其中 body 必须传入 progress-delta 项，可以有 name，description，id，update"
            :parameters  {:query (with-token)
                          :path  {:item-id string?}
                          :body  any?}
            :handler     (fn [{{{:keys [item-id]} :path
                                body              :body} :parameters}]
                           (hr/response (week/handle-add-week-plan-item-log
                                          item-id body)))}}]
   ["/update-item/:item-id/update-log"
    {:post {:summary     "更新本周计划项目：更新记录"
            :description "更新本周计划项目：更新记录。
           其中 body 必须传入 id, progress-delta 项，可以有 name，description，update"
            :parameters  {:query (with-token)
                          :path  {:item-id string?}
                          :body  any?}
            :handler     (fn [{{{:keys [item-id]} :path
                                body              :body} :parameters}]
                           (hr/response (week/handle-update-week-plan-item-log
                                          item-id body)))}}]
   ["/update-item/:item-id/remove-log/:log-id"
    {:post {:summary     "更新本周计划项目：删除记录"
            :description "更新本周计划项目：删除记录"
            :parameters  {:query (with-token :opt [:date string?])
                          :path  {:item-id string? :log-id string?}}
            :handler     (fn [{{{:keys [item-id log-id]} :path
                                {date :date}             :query} :parameters}]
                           (hr/response (week/handle-remove-week-plan-item-log
                                          item-id log-id (if (or (nil? date) (str/blank? date)) nil date))))}}]])

(def books-route
  ["/books"
   {:swagger {:tags ["藏书服务"]}}
   ["/updating-with-calibre-db"
    {:post {:summary    "上传 Calibre 数据库"
            :parameters {:multipart {:file     multipart/temp-file-part
                                     :truncate boolean?}}
            :handler    (fn [{{{:keys [file truncate]} :multipart} :parameters}]
                          (let [{:keys [filename tempfile]} file]
                            (hr/response (book/handle-upload-file filename tempfile truncate))))}}]
   ["/search-author/:search"
    {:get {:summary     "搜索作者"
           :description "根据作者进行书籍搜索"
           :parameters  {:path {:search string?}}
           :handler     (fn [{{{search :search} :path} :parameters}]
                          (hr/response (book/handle-search :author search true)))}}]
   ["/search-title/:search"
    {:get {:summary     "搜索书籍"
           :description "根据书籍名称进行书籍搜索"
           :parameters  {:path {:search string?}}
           :handler     (fn [{{{search :search} :path} :parameters}]
                          (hr/response (book/handle-search :title search true)))}}]
   ["/search"
    {:get {:summary     "搜索书籍(综合)"
           :description "根据书籍名称或作者进行书籍搜索"
           ;see file_share.cljc
           :parameters  {:query {:q string? :sort string?}}
           :handler     (fn [{{{search :q sort :sort} :query} :parameters}]
                          (hr/response (book/handle-search :unify search sort)))}}]])

(def disks-route
  ["/disks"
   {:swagger {:tags ["文件服务"]}}
   ["/updating-disk-metadata"
    {:post {:summary    "上传磁盘文件元数据"
            :parameters {:body {:truncate    boolean?
                                :files       any?
                                :upload-info any?}}
            :handler    (fn [{{{:keys [files upload-info truncate]} :body} :parameters}]
                          (hr/response (disk/handle-upload files upload-info truncate)))}}]
   ["/search"
    {:get {:summary     "搜索文件(综合)"
           :description "根据路径或名称进行文件搜索
            参数：q 查询关键词，sort 排序方法，kind 查找方式，size 过滤文件大小
            range-x 查找范围，range-y 查找磁盘 take 获取 drop 跳过"
           ;see file_share.cljc
           :parameters  {:query any?}
           :handler     (fn [{{query :query} :parameters}]
                          (hr/response (disk/handle-search query)))}}]])

(def short-route
  ["/short"
   {:swagger {:tags ["短链接服务"]}}
   ["/search/:id"
    {:get {:summary     "搜索短链接"
           :description "查找短链接"
           :parameters  {:path {:id string?}}
           :handler     (fn [{{{id :id} :path} :parameters}]
                          (hr/response (disk/handle-short-search id)))}}]])

(def goal-route
  ["/goal"
   {:tags #{"Collector"}}
   ["/goals"
    {:get  {:summary     "获取所有的 Collector Goals"
            :description "获取所有的 Collector Goals"
            :parameters  {:query (with-token)}
            :handler     (fn [{{_ :query} :parameters}]
                           (hr/response (goal/all-goals)))}
     :post {:summary     "创建/更新/删除 Collector Goal"
            :description "创建/更新/删除 Collector Goal，delete? 存在则删除，id 存在则更新，反之创建"
            :parameters  {:body any?}
            :handler     (fn [{{data :body} :parameters}]
                           (hr/response
                             (cond (and (:delete? data) (:id data))
                                   (goal/drop-goal (:id data))
                                   (:id data)
                                   (goal/update-goal (:id data) data)
                                   :else
                                   (goal/create-goal data))))}}]
   ["/goals/:goal-id/logs"
    {:get  {:summary     "获取某一条 Collector Goal 的 Logs"
            :description "获取某一条 Collector Goal 的 Logs"
            :handler     todo}
     :post {:summary     "创建/更新/删除某一条 Collector Goal 的 Log"
            :description "创建/更新/删除某一条 Collector Goal 的 Log，delete? 存在则删除，id 存在则更新，反之创建"
            :parameters  {:body any? :path {:goal-id int?}}
            :handler     (fn [{{data :body path :path} :parameters}]
                           (hr/response
                             (cond (and (:delete? data) (:id data))
                                   (goal/drop-goal-log (:id data))
                                   (:id data)
                                   (goal/update-goal-log (:id data) (merge path data))
                                   :else
                                   (goal/create-goal-log (:goal-id path) data))))}}]])

(defn cyber-routes []
  (conj
    basic-route
    to-do-route
    tickets-route
    check-route
    auto-route
    express-route
    location-route
    note-route
    movie-route
    notice-route
    clean-route
    fitness-route
    blue-route
    client-route
    dashboard-route
    diary-route
    task-route
    week-plan-route
    books-route
    disks-route
    short-route
    goal-route))
