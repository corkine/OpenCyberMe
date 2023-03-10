(ns cyberme.routes.services
  (:require
    [cyberme.middleware :as middleware]
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
    [clojure.java.io :as io]
    [cyberme.config :as config]
    [cyberme.cyber.file :as file]))

(defn service-routes []
  ["/api"
   {:coercion   spec-coercion/coercion
    :muuntaja   formats/instance
    :swagger    {:id ::api}
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
                 auth/wrap-logged
                 middleware/wrap-as-async]}

   ;; swagger documentation
   ["" {:no-doc  true
        :swagger {:info {:title       "cyberme-goods-api"
                         :description "https://mazhangjing.com"}}}

    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]

    ["/api-docs/*"
     {:get (swagger-ui/create-swagger-ui-handler
             {:url    "/api/swagger.json"
              :config {:validator-url nil}})}]]

   ["/places"
    {:auth/logged true
     :get         {:summary "?????????????????????????????????"
                   :handler (fn [_]
                              (hr/response (goods/all-place-with-goods)))}}]

   ["/place"
    [""
     {:auth/logged true
      :post        {:summary    "???????????????"
                    :parameters {:body any?}
                    :handler    (fn [{{body :body} :parameters}]
                                  (hr/response (goods/add-place body)))}}]
    ["/:id"
     {:auth/logged true
      :post        {:summary    "????????????"
                    :parameters {:path any?
                                 :body any?}
                    :handler    (fn [{{body :body} :parameters}]
                                  (hr/response (goods/edit-place body)))}}]

    ["/:id/delete"
     {:auth/logged true
      :post        {:summary    "????????????"
                    :parameters {:path any?}
                    :handler    (fn [{{id-data :path} :parameters}]
                                  (hr/response (goods/delete-place id-data)))}}]]

   ["/package"
    [""
     {:auth/logged true
      :post        {:summary    "???????????????"
                    :parameters {:body any?}
                    :handler    (fn [{{body :body} :parameters}]
                                  (hr/response (goods/add-package body)))}}]
    ["/:id/delete"
     {:auth/logged true
      :post        {:summary    "??????????????????"
                    :parameters {:path any?}
                    :handler    (fn [{{data :path} :parameters}]
                                  (hr/response (goods/delete-package data)))}}]]

   ["/packages"
    {:auth/logged true
     :get         {:summary    "??????????????????"
                   :parameters {:query any?}
                   :handler    (fn [{{body :query} :parameters}]
                                 (hr/response (goods/get-packages body)))}}]

   ["/recent"
    {:auth/logged true
     :get         {:summary    "???????????????????????????"
                   :parameters {:query any?}
                   :handler    (fn [{{body :query} :parameters}]
                                 (hr/response (goods/get-recent body)))}}]

   ["/good"
    [""
     {:auth/logged true
      :post        {:summary    "????????????"
                    :parameters {:body any?}
                    :handler    (fn [{{body :body} :parameters}]
                                  (hr/response (goods/add-good body)))}}]

    ["/:id"
     {:auth/logged true
      :post        {:summary    "????????????"
                    :parameters {:body any?}
                    :handler    (fn [{{body :body} :parameters}]
                                  (hr/response (goods/edit-good body)))}}]

    ["/:id/delete"
     {:auth/logged true
      :post        {:summary    "????????????"
                    :parameters {:path any?}
                    :handler    (fn [{{path :path} :parameters}]
                                  (hr/response (goods/delete-good path)))}}]
    ["/:id/hide"
     {:auth/logged true
      :post        {:summary    "??????(??????)??????"
                    :parameters {:path any?}
                    :handler    (fn [{{path :path} :parameters}]
                                  (hr/response (goods/hide-good path)))}}]

    ["/:id/move/:placeId"
     {:auth/logged true
      :post        {:summary    "????????????"
                    :parameters {:path any?}
                    :handler    (fn [{{path :path} :parameters}]
                                  (hr/response (goods/move-good path)))}}]

    ["/:id/box/:box-id"
     {:auth/logged true
      :get         {:summary    "??????"
                    :parameters {:path any?}
                    :handler    (fn [{{path :path} :parameters}]
                                  (hr/response (goods/box-good path)))}}]

    ["/:id/plan/:box-id"
     {:auth/logged true
      :get         {:summary    "????????????"
                    :parameters {:path any?}
                    :handler    (fn [{{path :path} :parameters}]
                                  (hr/response (goods/box-good (assoc path :is-plan true))))}}]

    ["/:id/unbox/:box-id"
     {:auth/logged true
      :get         {:summary    "????????????"
                    :parameters {:path any?}
                    :handler    (fn [{{path :path} :parameters}]
                                  (hr/response (goods/unbox-good path)))}}]]

   ["/usage"
    {:get {:handler (fn [_]
                      (hr/response (goods/fetch-usage)))}}]
   ["/wishlist"
    {:auth/logged true
     :post        {:parameters {:body any?}
                   :handler    (fn [{{body :body} :parameters}]
                                 (hr/response (db/insert-wishlist body)))}
     :get         {:handler (fn [_]
                              (hr/response (db/find-all-wish)))}}]
   ["/files"
    {:swagger {:tags ["OSS ??????"]}}

    ["/upload"
     {:post {:summary    "??????????????? OSS"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :handler    (fn [{{{:keys [file]} :multipart} :parameters}]
                           (let [{:keys [size filename tempfile]} file]
                             (hr/response (file/handle-upload size filename tempfile))))}}]

    #_["/download"
     {:get {:summary "??? OSS ???????????????"
            :swagger {:produces ["image/png"]}
            :handler (fn [_]
                       {:status  200
                        :headers {"Content-Type" "image/png"}
                        :body    (-> "public/img/warning_clojure.png"
                                     (io/resource)
                                     (io/input-stream))})}}]]])
