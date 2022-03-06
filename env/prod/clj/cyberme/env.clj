(ns cyberme.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[cyberme started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[cyberme has shut down successfully]=-"))
   :middleware identity})
