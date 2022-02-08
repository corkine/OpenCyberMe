(ns icemanager.routes.services
  (:require
    [reitit.swagger :as swagger]
    [reitit.swagger-ui :as swagger-ui]
    [reitit.ring.coercion :as coercion]
    [reitit.coercion.spec :as spec-coercion]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.multipart :as multipart]
    [reitit.ring.middleware.parameters :as parameters]
    [icemanager.middleware.formats :as formats]
    [ring.util.response :as hr]
    [icemanager.feature :as feature]
    [ring.util.http-response :refer :all]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [icemanager.auth :as auth]
    [icemanager.doc :as doc]
    [icemanager.db.core :as db]
    [icemanager.goods :as goods]))

(defn service-routes []
  ["/api"
   {:coercion   spec-coercion/coercion
    :muuntaja   formats/instance
    :swagger    {:id ::api}
    :middleware [;; query-params & form-params
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
        :swagger {:info {:title       "iceManager-api"
                         :description "https://mazhangjing.com"}}}

    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]

    ["/api-docs/*"
     {:get (swagger-ui/create-swagger-ui-handler
             {:url    "/api/swagger.json"
              :config {:validator-url nil}})}]]

   ["/ping"
    {:get (constantly (ok {:message "pong"}))}]


   ["/math"
    {:swagger {:tags ["math"]}}

    ["/plus"
     {:get  {:summary    "plus with spec query parameters"
             :parameters {:query {:x int?, :y int?}}
             :responses  {200 {:body {:total pos-int?}}}
             :handler    (fn [{{{:keys [x y]} :query} :parameters}]
                           {:status 200
                            :body   {:total (+ x y)}})}
      :post {:summary    "plus with spec body parameters"
             :parameters {:body {:x int?, :y int?}}
             :responses  {200 {:body {:total pos-int?}}}
             :handler    (fn [{{{:keys [x y]} :body} :parameters}]
                           {:status 200
                            :body   {:total (+ x y)}})}}]]

   ["/places"
    {:auth/logged true
     :get         {:summary "获取所有位置的所有物品"
                   :handler (fn [_]
                              (hr/response (goods/all-place-with-goods)))}}]

   ["/place"
    [""
     {:auth/logged true
      :post        {:summary    "添加新位置"
                    :parameters {:body any?}
                    :handler    (fn [{{body :body} :parameters}]
                                  (hr/response (goods/add-place body)))}}]
    ["/:id"
     {:auth/logged true
      :post        {:summary    "更改位置"
                    :parameters {:path any?
                                 :body any?}
                    :handler    (fn [{{body :body} :parameters}]
                                  (hr/response (goods/edit-place body)))}}]

    ["/:id/delete"
     {:auth/logged true
      :post        {:summary    "删除位置"
                    :parameters {:path any?}
                    :handler    (fn [{{id-data :path} :parameters}]
                                  (hr/response (goods/delete-place id-data)))}}]]

   ["/package"
    [""
     {:auth/logged true
      :post        {:summary    "添加新打包"
                    :parameters {:body any?}
                    :handler    (fn [{{body :body} :parameters}]
                                  (hr/response (goods/add-package body)))}}]
    ["/:id/delete"
     {:auth/logged true
      :post        {:summary    "删除某个打包"
                    :parameters {:path any?}
                    :handler    (fn [{{data :path} :parameters}]
                                  (hr/response (goods/delete-package data)))}}]]

   ["/packages"
    {:auth/logged true
     :get         {:summary    "获取最近打包"
                   :parameters {:query any?}
                   :handler    (fn [{{body :query} :parameters}]
                                 (hr/response (goods/get-packages body)))}}]

   ["/recent"
    {:auth/logged true
     :get         {:summary    "获取最近打包和位置"
                   :parameters {:query any?}
                   :handler    (fn [{{body :query} :parameters}]
                                 (hr/response (goods/get-recent body)))}}]

   ["/good"
    [""
     {:auth/logged true
      :post        {:summary    "新建物品"
                    :parameters {:body any?}
                    :handler    (fn [{{body :body} :parameters}]
                                  (hr/response (goods/add-good body)))}}]

    ["/:id"
     {:auth/logged true
      :post        {:summary    "修改物品"
                    :parameters {:body any?}
                    :handler    (fn [{{body :body} :parameters}]
                                  (hr/response (goods/edit-good body)))}}]

    ["/:id/delete"
     {:auth/logged true
      :post        {:summary    "删除物品"
                    :parameters {:path any?}
                    :handler    (fn [{{path :path} :parameters}]
                                  (hr/response (goods/delete-good path)))}}]
    ["/:id/hide"
     {:auth/logged true
      :post        {:summary    "删除(隐藏)物品"
                    :parameters {:path any?}
                    :handler    (fn [{{path :path} :parameters}]
                                  (hr/response (goods/hide-good path)))}}]

    ["/:id/move/:placeId"
     {:auth/logged true
      :post        {:summary    "移动物品"
                    :parameters {:path any?}
                    :handler    (fn [{{path :path} :parameters}]
                                  (hr/response (goods/move-good path)))}}]

    ["/:id/box/:box-id"
     {:auth/logged true
      :get {:summary "打包"
            :parameters {:path any?}
            :handler (fn [{{path :path} :parameters}]
                       (hr/response (goods/box-good path)))}}]

    ["/:id/plan/:box-id"
     {:auth/logged true
      :get {:summary "准备打包"
            :parameters {:path any?}
            :handler (fn [{{path :path} :parameters}]
                       (hr/response (goods/box-good (assoc path :is-plan true))))}}]

    ["/:id/unbox/:box-id"
     {:auth/logged true
      :get {:summary "取消打包"
            :parameters {:path any?}
            :handler (fn [{{path :path} :parameters}]
                       (hr/response (goods/unbox-good path)))}}]]

   ["/feature"
    [""
     {:auth/logged true
      :post        {:summary    "添加特性"
                    :parameters {:body any?}
                    :handler    (fn [{{body :body} :parameters}]
                                  #_(log/info "body: " body)
                                  (hr/response (feature/add-feature body)))}}]
    ["/:rs-id-lower"
     {:auth/logged true
      :get         {:summary    "获取特性特性信息"
                    :parameters {:path {:rs-id-lower string?}}
                    :handler    (fn [{{{:keys [rs-id-lower]} :path} :parameters}]
                                  (hr/response (feature/feature-by-rs-id rs-id-lower)))}
      :post        {:summary    "特性更新"
                    :parameters {:path {:rs-id-lower string?}
                                 :body map?}
                    :handler    (fn [{{body                  :body
                                       {:keys [rs-id-lower]} :path} :parameters}]
                                  #_(hr/not-found "Not Found")
                                  (hr/response (feature/update-feature rs-id-lower body)))}}]

    ["/:id/delete"
     {:auth/logged true
      :post        {:summary    "删除特性"
                    :parameters {:path {:id string?}}
                    :handler    (fn [{{data :path} :parameters}]
                                  (hr/response (feature/delete-feature data)))}}]
    ["/:rs-id-lower/tr.docx"
     {:get {:summary    "下载 TR 文档"
            :swagger    {:produces ["application/vnd.openxmlformats-officedocument.wordprocessingml.document"]}
            :parameters {:path {:rs-id-lower string?}}
            :handler    (fn [{{{:keys [rs-id-lower]} :path} :parameters}]
                          {:status  200
                           :headers {"Content-Type"
                                     "application/vnd.openxmlformats-officedocument.wordprocessingml.document"}
                           :body    (doc/resp-tr-doc rs-id-lower)
                           #_(-> "public/img/warning_clojure.png"
                                 (io/resource)
                                 (io/input-stream))})}}]
    ["/:rs-id-lower/review.pdf"
     {:get {:summary    "下载 Review 文档"
            :swagger    {:produces ["application/pdf"]}
            :parameters {:path {:rs-id-lower string?}}
            :handler    (fn [{{{:keys [rs-id-lower]} :path} :parameters}]
                          (doc/resp-review-pdf rs-id-lower))}}]]
   ["/features"
    {:auth/logged true
     :get         {:summary "获取所有特性"
                   :handler (fn [_]
                              (hr/response (feature/all-features)))}}]
   ["/usage"
    {:get {:handler (fn [_]
                      (hr/response (feature/fetch-usage)))}}]
   ["/wishlist"
    {:auth/logged true
     :post        {:parameters {:body any?}
                   :handler    (fn [{{body :body} :parameters}]
                                 (hr/response (db/insert-wishlist body)))}
     :get         {:handler (fn [_]
                              (hr/response (db/find-all-wish)))}}]
   ["/files"
    {:swagger {:tags ["files"]}}

    ["/upload"
     {:post {:summary    "upload a file"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :responses  {200 {:body {:name string?, :size int?}}}
             :handler    (fn [{{{:keys [file]} :multipart} :parameters}]
                           {:status 200
                            :body   {:name (:filename file)
                                     :size (:size file)}})}}]

    ["/download"
     {:get {:summary "downloads a file"
            :swagger {:produces ["image/png"]}
            :handler (fn [_]
                       {:status  200
                        :headers {"Content-Type" "image/png"}
                        :body    (-> "public/img/warning_clojure.png"
                                     (io/resource)
                                     (io/input-stream))})}}]]])
