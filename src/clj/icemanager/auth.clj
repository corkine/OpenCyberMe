(ns icemanager.auth
  (:require [reitit.ring :as ring]
            [clojure.tools.logging :as log]
            [icemanager.db.core :as db]
            [icemanager.config :refer [env]]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]))

;(defn identity->roles [identity]
;  (cond-> #{:any}
;          (some? identity) (conj :authenticated)))
;(def roles
;  {:message/create!      #{:authenticated}
;   :auth/login           #{:any}
;   :auth/logout          #{:any}
;   :account/register     #{:any}
;   :session/get          #{:any}
;   :messages/list        #{:any}
;   :messages/by-author   #{:any}
;   :author/get           #{:any}
;   :account/set-profile! #{:authenticated}
;   :swagger/swagger      #{:any}
;   :media/get            #{:any}
;   :media/upload         #{:authenticated}})
;
;(defn authorized? [roles req]
;  (if (seq roles)
;    (->> req
;         :session
;         :identity
;         identity->roles
;         (some roles)
;         boolean)
;    (do
;      (log/error "roles: " roles " is empty for route: " (:uri req))
;      false)))
;
;(defn get-roles-from-match [req]
;  (-> req
;      (ring/get-match)
;      (get-in [:data ::auth/roles] #{})))
;
;(defn wrap-authorized [handler unauthorized-handler]
;  (fn [req]
;    (if (authorized? (get-roles-from-match req) req)
;      (handler req)
;      (unauthorized-handler req))))

(defn get-logs-from-match [req]
  (-> req
      (ring/get-match)
      (get-in [:data :auth/logged] false)))

(defn wrap-logged [handler]
  (fn [req]
    (if (get-logs-from-match req)
      (try
        #_(log/info req)
        (db/add-log {:api (:uri req) :info (select-keys req [:request-method
                                                             :scheme
                                                             :remote-addr
                                                             :server-name
                                                             :server-port
                                                             :session/key])})
        (catch Exception e (log/error "log req error: " (str e)))))
    (handler req)))

(defn authenticated? [name pass]
  (and (= name (or (:auth-user env) "admin"))
       (= pass (or (:auth-pass env) "admin"))))

(defn wrap-basic-auth [handler]
  (wrap-basic-authentication handler authenticated?))
