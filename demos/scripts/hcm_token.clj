;clojure -Sdeps '{:paths ["."] :deps {etaoin/etaoin {:mvn/version,"1.0.39"}}}' -M -m hcm-token
(ns hcm-token
  (:require
    [clj-http.client :as c]
    [clojure.string :as str]
    [etaoin.api :as e]
    [etaoin.dev :as dev]))

(def config {:url         "https://inspur.hcmcloud.cn/"
             :user        "YOUR PHONE NUMBER"
             :pass        "YOUR PASSWORD"
             :driver      "WHERE THE chromedriver EXISTS"
             :bin         ""
             :cyber-token "CYBERME TOKEN"
             :cyber-url   "CYBERME TOKEN URL"
             :debug       false})

(defn -main []
      (println "[info] starting chrome now...")
      (let [driver ((if (:debug config)
                      e/chrome
                      e/chrome-headless)
                    {:dev
                     {:perf
                      {:level      :info
                       :network?   true
                       :interval   1000
                       :categories [:devtools.network]}} :path-driver (:driver config)})]
           (println "[info] visiting url")
           (e/go driver (:url config))
           (e/wait-visible driver {:id :tel})
           (e/wait-visible driver {:id :password})
           (println "[info] login now")
           (e/fill driver {:id :tel} (:user config))
           (e/fill driver {:id :password} (:pass config))
           (e/click driver {:id :login_submit})
           (e/wait-visible driver {:id :nav-list})
           (println "[info] login done")
           (let [messages (dev/get-performance-logs driver)
                 token (let [cookie-map-list
                             (filterv (comp not nil?)
                                      (map (fn [log]
                                               (if-let [h (-> log :message :params :headers)]
                                                       (if (str/includes? (or (-> h :cookie) "") "token=")
                                                         {:referer (:referer h)
                                                          :cookie  (:cookie h)}))) messages))
                             cookie (-> cookie-map-list last :cookie)]
                            (second (re-find #"token=\"(.*?)\"" cookie)))]
                (e/quit driver)
                (println "[info] token is " token)
                (if token
                  (println "[info] set result: "
                           (-> (c/get (:cyber-url config)
                                      {:headers      {"Authorization" (str "Basic " (:cyber-token config))}
                                       :query-params {"token" token}})
                               :body))
                  (println "[error] " "no token extracted from hcm login.")))))
