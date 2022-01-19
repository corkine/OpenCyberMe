(ns icemanager.feature
      (:require
        [cheshire.core :refer [generate-string parse-string]]
        [next.jdbc.date-time]
        [next.jdbc.prepare]
        [next.jdbc.result-set]
        [clojure.tools.logging :as log]
        [conman.core :as conman]
        [icemanager.config :refer [env]]
        [icemanager.db.core :as db]
        [mount.core :refer [defstate]]))

(defn all-features []
  (db/all-features))
