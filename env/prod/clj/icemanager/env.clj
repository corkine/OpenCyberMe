(ns icemanager.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[icemanager started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[icemanager has shut down successfully]=-"))
   :middleware identity})
