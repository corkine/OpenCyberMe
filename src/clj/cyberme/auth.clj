(ns cyberme.auth
  (:require [reitit.ring :as ring]
            [clojure.tools.logging :as log]
            [cyberme.db.core :as db]
            [cyberme.tool :as tool]
            [cyberme.config :refer [env]]
            [clojure.string :as s]
            [clojure.string :as str])
  (:import java.util.Base64))

(defn get-logs-from-match [req]
  (-> req
      (ring/get-match)
      (get-in [:data :auth/logged] false)))

(defn wrap-logged [handler]
  (fn
    ([req]
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
     (handler req))
    ([req response raise]
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
     (handler req response raise))))

#_(defn authenticated? [name pass]
    (let [n (:auth-user env)
          p (:auth-pass env)]
      (and (not (nil? n)) (not (nil? p))
           (= name n) (= pass p))))

(defn authenticated?
  "Basic 验证，密文
  密文检查使得本函数消耗从 0.01ms -> 0.07ms"
  [name pass role]
  (let [auth-info (or (:auth-info env) [])
        filter-fn #(if (nil? role)
                     (and (= name (:user %)) (tool/pass-right? pass (:pass %)))
                     (and (= name (:user %)) (tool/pass-right? pass (:pass %)) (= role (:role %))))
        pass? (some? (some filter-fn auth-info))]
    pass?))

(defn auth-in-query
  "Query 验证，明文"
  [query-param]
  (let [name (get query-param "user")
        pass (get query-param "secret")
        auth-info (or (:auth-info env) [])
        filter-fn #(and (= name (:user %)) (= pass (:pass %)))
        pass? (some? (some filter-fn auth-info))]
    pass?))

(defn auth-in-query-task
  "TASK 任务 Query 验证，明文"
  [query-param]
  (let [name (get query-param "user")
        pass (get query-param "secret")
        auth-info (or (:auth-info-task env) [])
        filter-fn #(and (= name (:user %)) (= pass (:pass %)))
        pass? (some? (some filter-fn auth-info))]
    pass?))

(defn byte-transform
  [direction-fn string]
  (try
    (s/join (map char (direction-fn (.getBytes ^String string))))
    (catch Exception _)))

(defn encode-base64
  [^String string]
  (byte-transform #(.encode (Base64/getEncoder) ^bytes %) string))

(defn decode-base64
  [^String string]
  (byte-transform #(.decode (Base64/getDecoder) ^bytes %) string))

(defn basic-authentication-request
  [request auth-fn]
  (let [auth ((:headers request) "authorization")
        cred (and auth (decode-base64 (last (re-find #"^Basic (.*)$" auth))))
        [user pass] (and cred (s/split (str cred) #":" 2))
        is-super? (str/ends-with? (or user "") "__")
        user (if is-super? (.subSequence user 0 (- (.length user) 2)) user)
        is-authed? (and cred (auth-fn (str user) (str pass)))]
    (assoc request :basic-authentication is-authed?
                   :auth-info (when is-authed? {:user user :is-super? is-super?}))))

(defn authentication-failure
  [& [realm denied-response]]
  (assoc (merge {:status 403
                 :body   "access denied"}
                denied-response)
    :headers (merge {"WWW-Authenticate" (format "Basic realm=\"%s\""
                                                (or realm "restricted area"))
                     "Content-Type"     "text/plain"}
                    (:headers denied-response))))

(defn authentication-failure-401
  [& [realm denied-response]]
  (assoc (merge {:status 401
                 :body   "access denied"}
                denied-response)
    :headers (merge {"WWW-Authenticate" (format "Basic realm=\"%s\""
                                                (or realm "restricted area"))
                     "Content-Type"     "text/plain"}
                    (:headers denied-response))))

(defn is-swagger [{uri :uri}]
  (condp #(s/starts-with? %2 %1) uri
    "/cyber/api-docs/" true
    "/api/api-docs/" true
    "/cyber/swagger.json" true
    "/api/swagger.json" true
    "/cyber/dashboard/psych-data-upload" true
    false))

(defn is-allowed [{uri :uri}]
  (condp #(s/starts-with? %2 %1) uri
    "/cyber/dashboard/psych-data-upload" true
    "/cyber/dashboard/psych-data-download" true
    false))

(defn is-task-authed [req]
  (and (s/starts-with? (:uri req) "/cyber/task/")
       (auth-in-query-task (if (empty? (:query-params req))
                             (:headers req)
                             (:query-params req)))))

(defn wrap-basic-authentication
  [app authenticate & [realm denied-response]]
  (fn
    ([{:keys [request-method] :as req}]
     (let [auth-req (basic-authentication-request req authenticate)]
       (if (or (:basic-authentication auth-req)
               (auth-in-query (:query-params req))
               (is-allowed req)
               (is-task-authed req))
         (app auth-req)
         (if (is-swagger req)
           (authentication-failure-401 realm
                                       (into denied-response
                                             (when (= request-method :head)
                                               {:body nil})))
           (authentication-failure realm
                                   (into denied-response
                                         (when (= request-method :head)
                                           {:body nil})))))))
    ([{:keys [request-method] :as req} respond raise]
     (let [auth-req (basic-authentication-request req authenticate)]
       (if (or (:basic-authentication auth-req)
               (auth-in-query (:query-params req))
               (is-allowed req)
               (is-task-authed req))
         (app req respond raise)
         (respond
           (if (is-swagger req)
             (authentication-failure-401 realm
                                         (into denied-response
                                               (when (= request-method :head)
                                                 {:body nil})))
             (authentication-failure realm
                                     (into denied-response
                                           (when (= request-method :head)
                                             {:body nil}))))))))))

(defn wrap-basic-auth [handler]
  (wrap-basic-authentication handler #(authenticated? %1 %2 nil)))