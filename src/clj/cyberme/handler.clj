(ns cyberme.handler
  (:require
    [cyberme.middleware :as middleware]
    [cyberme.layout :refer [error-page]]
    [cyberme.routes.home :refer [home-routes]]
    [cyberme.router :as share]
    [cyberme.routes.services :refer [service-routes]]
    [cyberme.routes.cyber :refer [cyber-routes]]
    [reitit.swagger-ui :as swagger-ui]
    [reitit.ring :as ring]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.webjars :refer [wrap-webjars]]
    [cyberme.env :refer [defaults]]
    [mount.core :as mount]
    [reitit.ring.middleware.dev :as r-dev]))

(mount/defstate init-app
  :start ((or (:init defaults) (fn [])))
  :stop  ((or (:stop defaults) (fn []))))

(defn- async-aware-default-handler
  ([_] nil)
  ([_ respond _] (respond nil)))

(mount/defstate app-routes
  :start
  (ring/ring-handler
    (ring/router
      [#_(home-routes)
       (share/share-router)
       (service-routes)
       (cyber-routes)]
      {:conflict nil
       ;:reitit.middleware/transform r-dev/print-request-diffs
       })
    (ring/routes
      (swagger-ui/create-swagger-ui-handler
        {:path   "/swagger-ui"
         :url    "/api/swagger.json"
         :config {:validator-url nil}})
      (ring/create-resource-handler
        {:path "/"})
      (wrap-content-type
        (wrap-webjars async-aware-default-handler))
      (ring/create-default-handler
        {:not-found
         (constantly (error-page {:status 404, :title "404 - Page not found"}))
         :method-not-allowed
         (constantly (error-page {:status 405, :title "405 - Not allowed"}))
         :not-acceptable
         (constantly (error-page {:status 406, :title "406 - Not acceptable"}))}))))

(defn app []
  (middleware/wrap-base #'app-routes))
