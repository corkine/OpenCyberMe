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
    [cyberme.inspur.core :as inspur]))

(s/def :global/user string?)
(s/def :global/secret string?)
(s/def :hcm/token string?)
(s/def :hcm/adjust int?)
(s/def :device/plainText boolean?)

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
    ["/hint"
     {:get {:summary    "当日生活提醒服务：HCM、健身、饮食和刷牙"
            :parameters {:query (s/keys :req-un [:global/user :global/secret]
                                        :opt-un [:hcm/token])}
            :handler    (fn [{{query :query} :parameters}]
                          (hr/response (inspur/handle-serve-hint query)))}}]
    ["/now"
     {:get {:summary    "获取当前打卡情况"
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
            :handler    todo
            #_(fn [{{query :query} :parameters}]
                (hr/response (inspur/handle-serve-day query)))}}]
    ["/summary"
     {:get {:summary    "HCM 所有信息统计"
            :parameters {:query (s/keys :req-un [:global/user :global/secret]
                                        :opt-un [:hcm/token :hcm/adjust])}
            :handler todo
            #_(fn [{{query :query} :parameters}]
                          (hr/response (inspur/handle-serve-day query)))}}]
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

   ["/express"
    {:get {:summary    "快递信息查询"
           :parameters {:query (s/keys :req-un [:global/user :global/secret]
                                       :opt-un [:hcm/token :hcm/adjust])}
           :handler todo
           #_(fn [{{query :query} :parameters}]
               (hr/response (inspur/handle-serve-day query)))}}]
   ["/note"
    {:get {:summary    "便签信息查询"
           :parameters {:query (s/keys :req-un [:global/user :global/secret]
                                       :opt-un [:hcm/token :hcm/adjust])}
           :handler todo
           #_(fn [{{query :query} :parameters}]
               (hr/response (inspur/handle-serve-day query)))}}]
   ["/notice"
    {:get {:summary    "Slack 通知服务"
           :parameters {:query (s/keys :req-un [:global/user :global/secret]
                                       :opt-un [:hcm/token :hcm/adjust])}
           :handler todo
           #_(fn [{{query :query} :parameters}]
               (hr/response (inspur/handle-serve-day query)))}}]

   ["/todo"
    {:get {:summary    "Microsoft TODO 服务"
           :parameters {:query (s/keys :req-un [:global/user :global/secret]
                                       :opt-un [:hcm/token :hcm/adjust])}
           :handler todo
           #_(fn [{{query :query} :parameters}]
               (hr/response (inspur/handle-serve-day query)))}}]])
