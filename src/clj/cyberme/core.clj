(ns cyberme.core
  (:require
    [cyberme.handler :as handler]
    [cyberme.nrepl :as nrepl]
    [luminus.http-server :as http]
    [luminus-migrations.core :as migrations]
    [cyberme.config :refer [env]]
    [clojure.tools.cli :refer [parse-opts]]
    [clojure.tools.logging :as log]
    [mount.core :as mount]
    [cyberme.cyber.todo :as todo]
    [cyberme.cyber.express :as express]
    [clojure.java.io :as io]
    [cheshire.core :as json]
    [cheshire.generate :refer [add-encoder encode-str]]
    [cyberme.cyber.task :as task]
    [cyberme.cyber.inspur :as inspur]
    [cyberme.config :refer [edn]]
    [cyberme.media.mini4k :as mini4k]
    [cyberme.cyber.weather :as weather])
  (:gen-class)
  (:import (java.time LocalDateTime)
           (java.time.format DateTimeFormatter)))

;; log uncaught exceptions in threads
(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread ex]
      (log/error {:what :uncaught-exception
                  :exception ex
                  :where (str "Uncaught exception on" (.getName thread))}))))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :parse-fn #(Integer/parseInt %)]])

(mount/defstate ^{:on-reload :noop} http-server
  :start
  (http/start
    (-> env
        (assoc  :handler (handler/app))
        (update :port #(or (-> env :options :port) %))
        (select-keys [:handler :host :port])))
  :stop
  (http/stop http-server))

(mount/defstate ^{:on-reload :noop} repl-server
  :start
  (when (env :nrepl-port)
    (nrepl/start {:bind (env :nrepl-bind)
                  :port (env :nrepl-port)}))
  :stop
  (when repl-server
    (nrepl/stop repl-server)))

(defn backup-token []
  (try
    (let [todo-cache @todo/cache
          hcm-cache @inspur/token-cache]
      (with-open [w (io/writer "cache.json" :append false)]
        (.write w (json/generate-string {:todo todo-cache
                                         :hcm hcm-cache})))
      (log/info "[backup-cache] saving cache to cache.json done."))
    (catch Exception e
      (log/error "[backup-cache] failed: " (.getMessage e)))))

(add-encoder LocalDateTime
             (fn [c jsonGenerator]
               (.writeString jsonGenerator (.format c (DateTimeFormatter/ISO_LOCAL_DATE_TIME)))))

(defn read-token []
  (try
    (let [data (slurp "cache.json")
          data-j (json/parse-string data true)]
      (reset! todo/cache (or (:todo data-j) {}))
      (reset! inspur/token-cache (or (:hcm data-j) {}))
      (log/info "[read-cache] reading cache from cache.json done."))
    (catch Exception e
      (log/error "[read-cache] failed: " (.getMessage e)))))

(mount/defstate ^{:on-reload :noop} backend-loop
                :start
                (let [enable-services (set (edn :enable-service))]
                  (log/info "[backend] starting all backend service " enable-services)
                  (read-token)
                  (when (contains? enable-services :todo)
                    (future
                      (Thread/sleep 2000)
                      (todo/backend-todo-service)))
                  (when (contains? enable-services :express)
                    (future
                      (Thread/sleep 2000)
                      (express/backend-express-service)))
                  (when (contains? enable-services :movie)
                    (future
                      (Thread/sleep 2000)
                      (mini4k/backend-mini4k-routine)))
                  (when (contains? enable-services :task)
                    (future
                      (Thread/sleep 2000)
                      (task/backend-task-routine)))
                  (when (contains? enable-services :auto)
                    (future
                      (Thread/sleep 2000)
                      (inspur/backend-hcm-auto-check-service)))
                  (when (contains? enable-services :weather)
                    (future
                      (Thread/sleep 2000)
                      (weather/backend-weather-routine))))
                :stop
                (do
                  (backup-token)
                  (log/info "[backend] stopped all backend service...")))

(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped"))
  (shutdown-agents))

(defn start-app [args]
  (doseq [component (-> args
                        (parse-opts cli-options)
                        mount/start-with-args
                        :started)]
    (log/info component "started"))
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))

(defn -main [& args]
  (mount/start #'cyberme.config/env)
  (cond
    (nil? (:database-url env))
    (do
      (log/error "Database configuration not found, :database-url environment variable must be set before running")
      (System/exit 1))
    (some #{"init"} args)
    (do
      (migrations/init (select-keys env [:database-url :init-script]))
      (System/exit 0))
    (migrations/migration? args)
    (do
      (migrations/migrate args (select-keys env [:database-url]))
      (System/exit 0))
    :else
    (start-app args)))
  
