(ns cyberme.routes.cyber
  (:require
    [reitit.swagger :as swagger]
    [reitit.swagger-ui :as swagger-ui]
    [reitit.ring.coercion :as coercion]
    [reitit.coercion.spec :as spec-coercion]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.multipart :as multipart]
    [reitit.ring.middleware.parameters :as parameters]
    [cyberme.middleware.formats :as formats]
    [ring.util.response :as hr]
    [ring.util.http-response :refer :all]
    [cyberme.auth :as auth]
    [cyberme.db.core :as db]
    [cyberme.goods :as goods]
    [clojure.spec.alpha :as s]
    [cyberme.cyber.inspur :as inspur]
    [cyberme.cyber.express :as express]
    [cyberme.cyber.slack :as slack]
    [cyberme.cyber.track :as track]
    [cyberme.cyber.todo :as todo]
    [cyberme.cyber.note :as note]
    [cyberme.cyber.mini4k :as mini4k]))

(s/def :global/user string?)
(s/def :global/secret string?)
(s/def :hcm/token string?)
(s/def :hcm/adjust int?)
(s/def :hcm/needCheckAt string?)
(s/def :hcm/kpi int?)
(s/def :auto/day int?)
(s/def :auto/date string?)
(s/def :auto/start string?)
(s/def :auto/end string?)
(s/def :summary/todayFirst boolean?)
(s/def :summary/use2MonthData boolean?)
(s/def :summary/useAllData boolean?)
(s/def :summary/showDetails boolean?)
(s/def :device/plainText boolean?)
(s/def :slack/from string?)
(s/def :slack/channel string?)
(s/def :slack/message string?)
(s/def :express/no string?)
(s/def :express/type string?)
(s/def :express/note string?)
(s/def :location/by string?)
(s/def :location/lo double?)
(s/def :location/la double?)
(s/def :location/al double?)
(s/def :location/ve double?)
(s/def :location/ho double?)
(s/def :todo/code string?)
(s/def :todo/focus boolean?)
(s/def :todo/showCompleted boolean?)
(s/def :note/quick boolean?)
(s/def :note/content string?)
(s/def :note/id int?)
(s/def :note/from string?)
(s/def :note/justContent boolean?)
(s/def :note/liveSeconds int?)
(s/def :movie/name string?)
(s/def :movie/url string?)


(defn not-impl [_] (hr/content-type (hr/not-found "没有实现。") "plain/text"))

(defn todo [_] (hr/content-type (hr/not-found "正在施工。") "plain/text"))

(defn cyber-routes []
  ["/cyber"
   {:coercion   spec-coercion/coercion
    :muuntaja   formats/instance
    :swagger    {:id ::cyber}
    :middleware [auth/wrap-basic-auth
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
                 auth/wrap-logged]}

   ;; swagger documentation
   ["" {:no-doc  true
        :swagger {:info {:title       "cyberme-api"
                         :description "https://mazhangjing.com"}}}

    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]

    ["/api-docs/*"
     {:get (swagger-ui/create-swagger-ui-handler
             {:url    "/cyber/swagger.json"
              :config {:validator-url nil}})}]]

   ["/check"
    {:tags #{"HCM 相关"}}
    ["/hint"
     {:get {:summary     "当日生活提醒服务：HCM、健身、饮食和刷牙"
            :description "返回当日生活信息，比如 HCM 打卡，健身，饮食和刷牙情况"
            :parameters  {:query (s/keys :req-un []
                                         :opt-un [:global/user :global/secret
                                                  :hcm/token])}
            :handler     (fn [{{query :query} :parameters}]
                           (hr/response (inspur/handle-serve-hint query)))}}]
    ["/now"
     {:get {:summary     "获取当前打卡情况 (Pixel)"
            :description "仅供 PIXEL 使用的，打卡后通知 Slack 的内部方法"
            :parameters  {:query (s/keys :req-un []
                                         :opt-un [:global/user :global/secret
                                                  :hcm/token :device/plainText])}
            :handler     (fn [{{{:keys [plainText] :as query} :query} :parameters}]
                           (let [res (inspur/handle-serve-today query)]
                             (if plainText
                               (hr/content-type (hr/response (:message res)) "plain/text")
                               (hr/response res))))}}]
    ["/today"
     {:get {:summary     "HCM 每日信息统计"
            :description "获取今日打卡情况"
            :parameters  {:query (s/keys :req-un []
                                         :opt-un [:global/user :global/secret
                                                  :hcm/token :hcm/adjust])}
            :handler     (fn [{{query :query} :parameters}]
                           (hr/response (inspur/handle-serve-day query)))}}]
    ["/thisWeek"
     {:get {:summary     "HCM 每周信息统计"
            :description "获取本周打卡情况"
            :parameters  {:query (s/keys :req-un []
                                         :opt-un [:global/user :global/secret
                                                  :hcm/token :hcm/adjust])}
            :handler     (fn [{{query :query} :parameters}]
                           (hr/response (inspur/handle-serve-this-week query)))}}]
    ["/summary"
     {:get {:summary     "HCM 所有信息统计"
            :description "获取 2021-06-01 日后所有打卡情况和打卡统计信息、加班信息"
            :parameters  {:query (s/keys :req-un []
                                         :opt-un [:global/user :global/secret
                                                  :hcm/token :hcm/kpi
                                                  :summary/use2MonthData
                                                  :summary/useAllData
                                                  :summary/showDetails])}
            :handler     (fn [{{query :query} :parameters}]
                           (hr/response (inspur/handle-serve-summary query)))}}]

    ["/set_token"
     {:get {:summary     "写入默认 Token"
            :description "写入默认的 Token 信息"
            :parameters  {:query (s/keys :req-un [:hcm/token]
                                         :opt-un [:global/user :global/secret])}
            :handler     (fn [{{query :query} :parameters}]
                           (hr/response (inspur/handle-set-cache query)))}}]

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
     {:get {:summary "本周计划加班信息（废弃）" :handler not-impl}}]]

   ["/auto"
    {:tags #{"HCM 相关"}}
    [""
     {:get {:summary     "上班状态自动检查 (Pixel)"
            :description "供 PIXEL 使用的内部接口，检查当前时间是否需要自动执行计划。"
            :parameters  {:query (s/keys :req-un [:hcm/needCheckAt]
                                         :opt-un [:global/user :global/secret])}
            :handler     (fn [{{query :query} :parameters}]
                           (hr/content-type
                             (hr/response (inspur/handle-serve-auto query))
                             "plain/text"))}}]
    ["/info"
     {:get  {:summary     "最近上班状态条件"
             :description "返回最近上班状态标记。"
             :parameters  {:query (s/keys :opt-un [:global/user :global/secret :auto/day])}
             :handler     (fn [{{query :query} :parameters}]
                            (hr/response (inspur/handle-serve-list-auto query)))}
      :post {:summary     "最近上班状态添加"
             :description "添加某天的状态标记，其中 date 格式为 20220101，start 和 end 格式为 10:01-10:22"
             :parameters  {:query (s/keys :req-un [:auto/date :auto/start :auto/end]
                                          :opt-un [:global/user :global/secret])}
             :handler     (fn [{{query :query} :parameters}]
                            (hr/response (inspur/handle-serve-set-auto query)))}}]
    ["/:date/delete"
     {:post {:summary     "删除某个上班状态"
             :description "删除某天的状态标记，日期格式必须为 20220101"
             :parameters  {:path {:date string?}
                           :query (s/keys :opt-un [:global/user :global/secret])}
             :handler     (fn [{{data :path} :parameters}]
                            (hr/response (inspur/handle-serve-delete-auto data)))}}]]

   ["/express"
    {:tags #{"快递追踪"}}
    ["/check"
     {:get {:summary     "快递信息查询"
            :description "传入 no 快递号以单次查询快递信息，type 为快递类型，默认不填为 AUTO"
            :parameters  {:query (s/keys :req-un [:express/no]
                                         :opt-un [:global/user :global/secret
                                                  :express/type])}
            :handler     (fn [{{query :query} :parameters}]
                           (hr/response (express/simple-check query)))}}]
    ["/track"
     {:get {:summary     "新增快递追踪"
            :description "追踪某一快递信息，调用后会立刻查询，且保存到数据库，如果快递尚未发货
            或者正在运输则进行自动追踪。type 为快递类型，默认为 AUTO，note 为通知时的快递别名。"
            :parameters  {:query (s/keys :req-un [:express/no]
                                         :opt-un [:global/user :global/secret
                                                  :express/type :express/note])}
            :handler     (fn [{{query :query} :parameters}]
                           (hr/response (express/simple-track query)))}}]

    ["/routine"
     {:get {:summary     "手动执行数据库快递追踪数据检查"
            :description "不应该直接调用，等同于内部接口周期性轮序追踪快递"
            :parameters  {:query (s/keys :opt-un [:global/user :global/secret])}
            :handler     (fn [_] (hr/response (express/track-routine)))}}]]

   ["/location"
    {:tags #{"快递追踪"}
     :get  {:summary     "鹰眼追踪"
            :description "上报 GPS 信息。"
            :parameters  {:query (s/keys :req-un [:location/by :location/lo :location/la]
                                         :opt-un [:location/al
                                                  :location/ve
                                                  :location/ho])}
            :handler     (fn [{{query :query} :parameters}]
                           (hr/response (track/handle-track query)))}}]

   ["/note"
    {:tags #{"笔记记录"}
     :get  {:summary    "便签信息查询"
            :parameters {:query (s/keys :req-un []
                                        :opt-un [:global/user :global/secret
                                                 :note/id :note/justContent
                                                 :note/quick :note/content])}
            :handler    (fn [{{query :query} :parameters}]
                          (let [just-content (:justContent query)
                                resp (hr/response (note/handle-fetch-note query))]
                            (if just-content (content-type resp "plain/text") resp)))}
     :post {:summary    "便签信息新建"
            :parameters {:query (s/keys :opt-un [:global/user :global/secret])
                         :body  (s/keys :req-un [:note/from :note/content]
                                        :opt-un [:note/id :note/liveSeconds])}
            :handler    (fn [{{body :body} :parameters}]
                          (hr/response (note/handle-add-note body)))}}]

   ["/movie"
    {:tags #{"电影电视更新通知"}}
    ["/"
     {:post {:summary    "添加电影电视跟踪"
             :parameters {:query (s/keys :opt-un [:global/user :global/secret]
                                         :req-un [:movie/name :movie/url])}
             :handler    (fn [{{query :query} :parameters}]
                           (hr/response (mini4k/handle-add-movie query)))}
      :get  {:summary    "获取跟踪列表"
             :parameters {:query (s/keys :opt-un [:global/user :global/secret])}
             :handler    (fn [_] (hr/response (mini4k/handle-list-movie)))}}]
    ["/:id/delete"
     {:post {:summary    "删除此跟踪电影"
             :parameters {:path  {:id int?}
                          :query (s/keys :opt-un [:global/user :global/secret])}
             :handler    (fn [{{path :path} :parameters}]
                           (hr/response (mini4k/handle-delete-movie path)))}}]]

   ["/notice"
    {:tags #{"Slack 消息通知"}
     :get  {:summary     "Slack 通知服务"
            :description "channel 为 PIXEL 则推送到 Pixel 通道，其余推送到服务器通道
            from 不提供默认为 Nobody"
            :parameters  {:query (s/keys :req-un [:slack/message]
                                         :opt-un [:global/user :global/secret
                                                  :slack/channel :slack/from])}
            :handler     (fn [{{query :query} :parameters}]
                           (hr/response (slack/serve-notice query)))}}]

   ["/todo"
    {:tags #{"TODO 同步"}}
    ["/setcode"
     {:get {:summary     "登录并保存 XToken"
            :description "不应该直接调用此接口，而应该使用 mazhangjing.com/todologin 来
            登录并且回调此接口并设置 AccessToken 和 RefreshToken。"
            :parameters  {:query (s/keys :opt-un [:global/user :global/secret
                                                  :todo/code])}
            :handler     (fn [{{query :query} :parameters}]
                           (hr/response (todo/handle-set-code query)))}}]
    ["/sync"
     {:get {:summary     "强制同步数据"
            :description "不应该直接调用此接口，此接口等同于内部线程自行同步更新方法调用。"
            :parameters  {:query (s/keys :opt-un [:global/user :global/secret])}
            :handler     (fn [{{query :query} :parameters}]
                           (hr/response (todo/handle-focus-sync)))}}]
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
            :parameters  {:query (s/keys :req-un []
                                         :opt-un [:global/user :global/secret
                                                  :todo/focus :todo/showCompleted])}
            :handler     (fn [{{query :query} :parameters}]
                           (hr/response (todo/handle-today query)))}}]]])
