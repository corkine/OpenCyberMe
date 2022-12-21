(ns cyberme.auto.hcm
  (:require
    [clojure.string :as str]
    [etaoin.api :as e]
    [etaoin.dev :as dev]
    [clojure.tools.logging :as log]
    [cyberme.cyber.inspur :as inspur]
    [cyberme.config :refer [edn-in]]))

(defn run-webdriver-fetch-token
  #_{:url         "https://inspur.hcmcloud.cn/"
   :user        "YOUR PHONE NUMBER"
   :pass        "YOUR PASSWORD"
   :driver      "WHERE THE chromedriver EXISTS"
   :bin         ""
   :cyber-token "CYBERME TOKEN"
   :cyber-url   "CYBERME TOKEN URL"
   :debug       false}
  [config]
  (log/info "[auto:hcm:info] staring chrome now...")
  (let [driver ((if (:debug config)
                  e/chrome
                  e/chrome-headless)
                {:dev
                 {:perf
                  {:level      :info
                   :network?   true
                   :interval   1000
                   :categories [:devtools.network]}} :path-driver (:driver config)})]
    (log/info "[auto:hcm:info] visiting url")
    (e/go driver (:url config))
    (e/wait-visible driver {:id :tel})
    (e/wait-visible driver {:id :password})
    (log/info "[auto:hcm:info] login now")
    (e/fill driver {:id :tel} (:user config))
    (e/fill driver {:id :password} (:pass config))
    (e/click driver {:id :login_submit})
    (e/wait-visible driver {:id :nav-list})
    (log/info "[auto:hcm:info] login done")
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
      token)))

(defn handle-set-token
  "自动调用 WebDriver 和 Chrome 登录 HCM 并获取 Token 保存到缓存，.edn 配置格式：
  {:auto {:hcm {:url https://inspur.hcmcloud.cn/
                :user 1537702xxx
                :pass xxx}
          :chrome {:driver /root/chromedriver_linux
                   :bin /opt/google/chrome/google-chrome
                   :debug false}}}"
  []
  (try
    (let [hcm-auto-config (edn-in [:auto :hcm])
          chrome-config (edn-in [:auto :chrome])
          config (merge hcm-auto-config chrome-config)]
      (if (empty? config)
        (log/warn "[hcm:auto] no config in .edn file, stop automatic sequence now...")
        (if-let [token (run-webdriver-fetch-token config)]
          (inspur/handle-set-cache {:token token})
          {:message "设置 Token 失败！"
           :status -1})))
    (catch Exception e
      (.printStackTrace e)
      {:message (str "设置 Token 失败！" (.getMessage e))
       :status -1})))


