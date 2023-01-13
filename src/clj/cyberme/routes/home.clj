(ns cyberme.routes.home
  (:require
   [cyberme.layout :as layout]
   [clojure.java.io :as io]
   [cyberme.middleware :as middleware]
   [ring.util.response]
   [ring.util.http-response :as response]
   [promesa.core :as p]))

(defn home-page [request]
  (layout/render request "home.html"))

(defn expensive [_]
  (p/delay 5000 {:status 200
                 :headers {"content-type" "text/plain"}
                 :body "Imagine this was a long running request.

But is uses promesa/CompletableFuture under the covers, so it does not tie
up a thread - it is async."}))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats
                 middleware/wrap-as-async]}
   ["/demo-home" {:get home-page}]
   ["/demo-async" {:get expensive}]
   ["/demo-docs" {:get (fn [_]
                    (-> (response/ok (-> "docs/docs.md" io/resource slurp))
                        (response/header "Content-Type" "text/plain; charset=utf-8")))}]])

