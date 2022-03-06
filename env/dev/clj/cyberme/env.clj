(ns cyberme.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [cyberme.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[cyberme started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[cyberme has shut down successfully]=-"))
   :middleware wrap-dev})
