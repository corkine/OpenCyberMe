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
    [cyberme.cyber.slack :as slack]))

(s/def :global/user string?)
(s/def :global/secret string?)
(s/def :hcm/token string?)
(s/def :hcm/adjust int?)
(s/def :hcm/needCheckAt string?)
(s/def :hcm/kpi int?)
(s/def :summary/todayFirst boolean?)
(s/def :summary/use2MonthData boolean?)
(s/def :summary/useAllData boolean?)
(s/def :summary/showDetails boolean?)
(s/def :device/plainText boolean?)
(s/def :slack/from string?)
(s/def :slack/channel string?)
(s/def :slack/message string?)

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
     {:get {:summary    "当日生活提醒服务：HCM、健身、饮食和刷牙"
            :parameters {:query (s/keys :req-un [:global/user :global/secret]
                                        :opt-un [:hcm/token])}
            :handler    (fn [{{query :query} :parameters}]
                          (hr/response (inspur/handle-serve-hint query)))}}]
    ["/now"
     {:get {:summary    "获取当前打卡情况 (Pixel)"
            :parameters {:query (s/keys :req-un [:global/user :global/secret]
                                        :opt-un [:hcm/token :device/plainText])}
            :handler    (fn [{{{:keys [plainText] :as query} :query} :parameters}]
                          (let [res (inspur/handle-serve-today query)]
                            (if plainText
                              (hr/content-type (hr/response (:message res)) "plain/text")
                              (hr/response res))))}}]
    ["/today"
     {:get {:summary    "HCM 每日信息统计"
            :parameters {:query (s/keys :req-un [:global/user :global/secret]
                                        :opt-un [:hcm/token :hcm/adjust])}
            :handler    (fn [{{query :query} :parameters}]
                          (hr/response (inspur/handle-serve-day query)))}}]
    ["/thisWeek"
     {:get {:summary    "HCM 每周信息统计"
            :parameters {:query (s/keys :req-un [:global/user :global/secret]
                                        :opt-un [:hcm/token :hcm/adjust])}
            :handler    (fn [{{query :query} :parameters}]
                          (hr/response (inspur/handle-serve-this-week query)))}}]
    ["/summary"
     {:get {:summary    "HCM 所有信息统计"
            :parameters {:query (s/keys :req-un [:global/user :global/secret]
                                        :opt-un [:hcm/token :hcm/kpi
                                                 :summary/use2MonthData
                                                 :summary/useAllData
                                                 :summary/showDetails])}
            :handler    (fn [{{query :query} :parameters}]
                          (hr/response (inspur/handle-serve-summary query)))}}]

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
    {:tags #{"HCM 相关"}
     :get  {:summary    "上班状态自动检查 (Pixel)"
            :parameters {:query (s/keys :req-un [:global/user :global/secret
                                                 :hcm/needCheckAt])}
            :handler    (fn [{{query :query} :parameters}]
                          (hr/content-type
                            (hr/response (inspur/handle-serve-auto query))
                            "plain/text"))}}]

   ["/express"
    {:tags #{"快递查询"}
     :get  {:summary    "快递信息查询"
            :parameters {:query (s/keys :req-un [:global/user :global/secret]
                                        :opt-un [:hcm/token :hcm/adjust])}
            :handler    todo
            #_(fn [{{query :query} :parameters}]
                (hr/response (inspur/handle-serve-day query)))}}]
   ["/note"
    {:tags #{"笔记记录"}
     :get  {:summary    "便签信息查询"
            :parameters {:query (s/keys :req-un [:global/user :global/secret]
                                        :opt-un [:hcm/token :hcm/adjust])}
            :handler    todo
            #_(fn [{{query :query} :parameters}]
                (hr/response (inspur/handle-serve-day query)))}}]
   ["/notice"
    {:tags #{"Slack 消息通知"}
     :get  {:summary    "Slack 通知服务"
            :parameters {:query (s/keys :req-un [:global/user :global/secret :slack/message]
                                        :opt-un [:slack/channel :slack/from])}
            :handler    (fn [{{query :query} :parameters}]
                          (hr/response (slack/serve-notice query)))}}]

   ["/todo"
    {:tags #{"TODO 同步"}
     :get  {:summary    "Microsoft TODO 服务"
            :parameters {:query (s/keys :req-un [:global/user :global/secret]
                                        :opt-un [:hcm/token :hcm/adjust])}
            :handler    todo
            #_(fn [{{query :query} :parameters}]
                (hr/response (inspur/handle-serve-day query)))}}]])
