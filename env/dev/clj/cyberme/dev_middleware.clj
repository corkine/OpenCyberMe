(ns cyberme.dev-middleware
  (:require
    [cyberme.config :refer [env]]
    [ring.middleware.reload :refer [wrap-reload]]
    [selmer.middleware :refer [wrap-error-page]]
    [prone.middleware :refer [wrap-exceptions]]))

(defn wrap-dev [handler]
  (-> handler
      wrap-reload
      wrap-error-page
      (cond-> (not (env :async?))
              (wrap-exceptions {:app-namespaces ['cyberme]}))))
