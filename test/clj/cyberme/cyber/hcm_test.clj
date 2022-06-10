(ns cyberme.cyber.hcm-test
  (:require
    [clojure.test :refer :all]
    [ring.mock.request :refer :all]
    [cyberme.handler :refer :all]
    [cyberme.middleware.formats :as formats]
    [muuntaja.core :as m]
    [cyberme.config :refer [edn]]
    [mount.core :as mount]
    [clojure.java.io :as io]
    [org.httpkit.client :as client]
    [cyberme.db.core :as db]
    [cheshire.core :as json]
    [clojure.string :as str]
    [cyberme.auth :as auth]
    [conman.core :as conman]
    [cyberme.cyber.track :as track]
    [cyberme.db.core :as db]
    [cyberme.cyber.inspur :as inspur]
    [cyberme.cyber.slack :as slack])
  (:import (java.util UUID)
           (java.time LocalDate LocalDateTime LocalTime)
           (java.time.format DateTimeFormatter)))

(defn parse-json [body]
  (m/decode formats/instance "application/json" body))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'cyberme.config/env
                 #'cyberme.handler/app-routes)
    (f)))

(defn prn-body [response]
  (println (slurp (io/reader (:body response)))))

(defn data [response]
  (with-open [r (io/reader (:body response))] (slurp r)))

(defn decode [response]
  (m/decode-response-body response))

(def fnn (fn [wrap-fn] (fn [& _] (wrap-fn))))

(deftest handle-serve-set-auto
  (testing "handle-serve-set-auto success"
    (with-redefs [db/set-auto (fn [&_] "RANDOM")]
      (let [data {:date  "20220101"
                  :start "10:01-10:32"
                  :end   "10:30-11:20"}
            {:keys [message status]} (inspur/handle-serve-set-auto data)]
        (is (str/includes? message "成功"))
        (is (str/includes? message "RANDOM")))))

  (testing "handle-serve-set-auto not right param"
    (with-redefs [db/set-auto (fn [&_] "RANDOM")]
      (let [data {:date  "2022-01-01"
                  :start "10:01-10:32"
                  :end   "10:30-11:20"}
            {:keys [message status]} (inspur/handle-serve-set-auto data)]
        (is (str/includes? message "失败")))))

  (testing "handle-serve-set-auto not right range"
    (with-redefs [db/set-auto (fn [&_] "RANDOM")]
      (let [data {:date  "20220101"
                  :start "10:34-10:32"
                  :end   "10:30-11:20"}
            {:keys [message status]} (inspur/handle-serve-set-auto data)]
        (is (str/includes? message "不合法"))))))

(deftest compute-work-hour-test
  (let [t6:30 (LocalTime/of 6 30)
        t7:00 (LocalTime/of 7 00)
        t7:30 (LocalTime/of 7 30)
        t8:20 (LocalTime/of 8 20)
        t8:30 (LocalTime/of 8 30)
        t9:30 (LocalTime/of 9 30)
        t10:00 (LocalTime/of 10 00)
        t12:00 (LocalTime/of 12 00)
        t13:10 (LocalTime/of 13 10)
        t17:30 (LocalTime/of 17 30)
        t19:30 (LocalTime/of 19 30)
        t20:00 (LocalTime/of 20 00)
        t->dt #(.atTime (inspur/ld-now) %)]
    (testing "正常上班：未打上班卡"
      (with-redefs [inspur/lt-now (fn [] t7:00)]
        (let [hcm-info []
              work-hour (inspur/compute-work-hour hcm-info true)]
          (is (= work-hour 0.0)))))
    (testing "正常上班：已打上班卡"
      (with-redefs [inspur/lt-now (fn [] t8:30)]
        (let [hcm-info [{:time (t->dt t8:20)}]
              work-hour (inspur/compute-work-hour hcm-info true)]
          (is (= work-hour 0.2)))))
    (testing "正常上班：未打下班卡"
      (with-redefs [inspur/lt-now (fn [] t20:00)]
        (let [hcm-info [{:time (t->dt t8:20)}]
              work-hour (inspur/compute-work-hour hcm-info true)]
          (is (= work-hour 9.0)))))
    (testing "正常上班：已打下班卡"
      (with-redefs [inspur/lt-now (fn [] t20:00)]
        (let [hcm-info [{:time (t->dt t8:20)}
                        {:time (t->dt t17:30)}]
              work-hour (inspur/compute-work-hour hcm-info true)]
          (is (= work-hour 7.5)))))

    (testing "非工作日：未打上班卡"
      (with-redefs [inspur/lt-now (fn [] t10:00)]
        (let [hcm-info []
              work-hour (inspur/compute-work-hour hcm-info false)]
          (is (= work-hour 0.0)))))
    (testing "非工作日：已打上班卡"
      (with-redefs [inspur/lt-now (fn [] t10:00)]
        (let [hcm-info [{:time (t->dt t9:30)}]
              work-hour (inspur/compute-work-hour hcm-info false)]
          (is (= work-hour 0.5)))))
    (testing "非工作日：未打下班卡"
      (with-redefs [inspur/lt-now (fn [] t17:30)]
        (let [hcm-info [{:time (t->dt t9:30)}]
              work-hour (inspur/compute-work-hour hcm-info false)]
          (is (= work-hour 6.3)))))
    (testing "非工作日：已打下班卡"
      (with-redefs [inspur/lt-now (fn [] t20:00)]
        (let [hcm-info [{:time (t->dt t9:30)}
                        {:time (t->dt t17:30)}]
              work-hour (inspur/compute-work-hour hcm-info false)]
          (is (= work-hour 6.3)))))

    (testing "工作日忘打上午卡，未上班(if1)"
      (with-redefs [inspur/lt-now (fn [] t8:20)]
        (let [hcm-info []
              work-hour (inspur/compute-work-hour hcm-info true)]
          (is (= work-hour 0.0)))))
    (testing "非工作日没打卡(if1)"
      (with-redefs [inspur/lt-now (fn [] t13:10)]
        (let [hcm-info []
              work-hour (inspur/compute-work-hour hcm-info false)]
          (is (= work-hour 0.0)))))
    (testing "工作日忘打上午卡，正上班(cond1)"
      (with-redefs [inspur/lt-now (fn [] t10:00)]
        (let [hcm-info []
              work-hour (inspur/compute-work-hour hcm-info true)]
          (is (= work-hour 1.5)))))
    (testing "工作日忘打上午卡，加班中(cond1)"
      (with-redefs [inspur/lt-now (fn [] t20:00)]
        (let [hcm-info []
              work-hour (inspur/compute-work-hour hcm-info true)]
          (is (= work-hour 8.8)))))
    (testing "工作日忘打上午卡，打了一次下班卡(cond2-1)"
      (with-redefs [inspur/lt-now (fn [] t20:00)]
        (let [hcm-info [{:time (t->dt t17:30)}]
              work-hour (inspur/compute-work-hour hcm-info true)]
          (is (= work-hour 7.3)))))
    (testing "工作日忘打下班卡(cond2-2-1)"
      (with-redefs [inspur/lt-now (fn [] t20:00)]
        (let [hcm-info [{:time (t->dt t8:20)}]
              work-hour (inspur/compute-work-hour hcm-info true)]
          (is (= work-hour 9.0)))))
    (testing "非工作日忘打下班卡(cond2-2-2)"
      (with-redefs [inspur/lt-now (fn [] t20:00)]
        (let [hcm-info [{:time (LocalDateTime/of 2000 01 01 8 20)}]
              work-hour (inspur/compute-work-hour hcm-info false)]
          (is (= work-hour 7.5)))))
    (testing "工作日忘打上午卡，打了两次下班卡(cond3)"
      (with-redefs [inspur/lt-now (fn [] t20:00)]
        (let [hcm-info [{:time (t->dt t17:30)}
                        {:time (t->dt t19:30)}]
              work-hour (inspur/compute-work-hour hcm-info true)]
          (is (= work-hour 8.3)))))
    (testing "工作日/非工作日正常打卡(cond3)"
      (with-redefs [inspur/lt-now (fn [] t20:00)]
        (let [hcm-info [{:time (LocalDateTime/of 2000 01 01 8 20)}
                        {:time (LocalDateTime/of 2000 01 01 17 30)}]
              work-hour (inspur/compute-work-hour hcm-info false)]
          (is (= work-hour 7.5)))))))

(deftest get-hcm-info
  (let [a (atom {})
        set (fn ([p v] (swap! a assoc p v) v)
              ([p v r] (swap! a assoc p v) r)
              ([p v m r] (swap! a assoc p v) (println m) r))
        got (fn [p v] (= (get @a p) v))
        non (fn [p v] (not= (get @a p) v))
        clean! #(reset! a {})]

    (testing "get-hcm-info with right token"
      (with-redefs [inspur/call-hcm (fn [_ _] {:status 200 :body (json/generate-string {})})
                    inspur/notice-expired-async #(set :notice :done)
                    inspur/hcm-info-from-cache (fn [_] nil)
                    inspur/set-hcm-cache (fnn #(set :cache :done))]
        (let [_ (clean!)
              {:keys [message status]}
              (inspur/get-hcm-info {:time (LocalDateTime/now) :token "good token"})]
          (is (= status 1))
          (is (got :cache :done))
          (is (non :notice :done)))))

    (testing "get-hcm-info with invalid token"
      (with-redefs [inspur/call-hcm (fn [_ _] {:status 400 :body nil})
                    inspur/notice-expired-async #(set :notice :done)
                    inspur/hcm-info-from-cache (fn [_] nil)
                    inspur/set-hcm-cache #(set :cache :done "set cache done." :done)]
        (let [_ (clean!)
              {:keys [message status]}
              (inspur/get-hcm-info {:time (LocalDateTime/now) :token "invalid token"})]
          (is (str/includes? message "get-hcm-info failed"))
          (is (= status 0))
          (is (non :cache :done))
          (is (got :notice :done)))))

    (testing "get-hcm-info with default token success"
      (with-redefs [inspur/call-hcm (fn [_ _] {:status 200 :body (json/generate-string {})})
                    inspur/notice-expired-async #(set :notice :set)
                    inspur/hcm-info-from-cache (fn [_] nil)
                    inspur/set-hcm-cache (fnn #(set :cache :done))
                    inspur/fetch-cache (fn [] {:token "123"})]
        (let [_ (clean!)
              {:keys [message status]}
              (inspur/get-hcm-info {:time (LocalDateTime/now)})
              _ (println message status)]
          (is (= status 1))
          (is (got :cache :done))
          (is (non :notice :set)))))

    (testing "get-hcm-info with default token but hcm expired"
      (with-redefs [inspur/call-hcm (fn [_ _] {:status 400 :body (json/generate-string {})})
                    inspur/notice-expired-async #(set :notice :set)
                    inspur/hcm-info-from-cache (fn [_] nil)
                    inspur/set-hcm-cache (fnn #(set :cache :done))
                    inspur/fetch-cache (fn [] {:token "123"})]
        (let [_ (clean!)
              {:keys [message status]}
              (inspur/get-hcm-info {:time (LocalDateTime/now)})]
          (is (= status 0))
          (is (non :cache :done))
          (is (got :notice :set)))))

    (testing "get-hcm-info with default token but token not-find"
      (with-redefs [inspur/call-hcm (fn [_ _] {:status 200 :body (json/generate-string {})})
                    inspur/notice-expired-async #(set :notice :set)
                    inspur/hcm-info-from-cache (fn [_] nil)
                    inspur/set-hcm-cache (fnn #(set :cache :done))
                    inspur/fetch-cache (fn [] {:token nil})]
        (let [_ (clean!)
              {:keys [message status]}
              (inspur/get-hcm-info {:time (LocalDateTime/now)})]
          (is (= status 0))
          (is (non :cache :done))
          (is (got :notice :set)))))))

(deftest handle-serve-auto
  (let [a (atom {})
        set (fn ([p v] (swap! a assoc p v) v)
              ([p v r] (swap! a assoc p v) r)
              ([p v m r] (swap! a assoc p v) (println m) r))
        got (fn [p v] (= (get @a p) v))
        non (fn [p v] (not= (get @a p) v))
        clean! #(reset! a {})]

    (testing "handle serve auto - time pass and no pass"
      ;测试传入参数是否能正常解析
      (with-redefs [db/get-today-auto (fn [& _] {:r1start (LocalTime/of 7 30)
                                                 :r1end   (LocalTime/of 8 10)
                                                 :r2start (LocalTime/of 17 30)
                                                 :r2end   (LocalTime/of 18 30)
                                                 :info    {}})
                    db/update-auto-info #(swap! a assoc :auto (:info %))]
        (let [_ (clean!)
              message (inspur/handle-serve-auto {:needCheckAt "7:33"
                                                 :mustInRange false})
              message-2 (inspur/handle-serve-auto {:needCheckAt "8:33"
                                                   :mustInRange false})
              message-3 (inspur/handle-serve-auto {:needCheckAt "17:30"
                                                   :mustInRange false})
              message-4 (inspur/handle-serve-auto {:needCheckAt "0:00"
                                                   :mustInRange false})
              message-5 (inspur/handle-serve-auto {:needCheckAt "7: 33"
                                                   :mustInRange false})
              message-6 (inspur/handle-serve-auto {:needCheckAt "8：10"
                                                   :mustInRange false})
              message-7 (inspur/handle-serve-auto {:needCheckAt "17 : 30"
                                                   :mustInRange false})
              message-8 (inspur/handle-serve-auto {:needCheckAt "0:00"
                                                   :mustInRange false})]
          (is (= message "YES"))
          (is (= message-2 "NO"))
          (is (= message-3 "YES"))
          (is (= message-4 "NO"))
          (is (= message-5 "YES"))
          (is (= message-6 "YES"))
          (is (str/includes? message-7 "解析数据时出现异常")))))

    (testing "handle serve auto - save request as check to database (in range)"
      ;在策略时间请求，不需要 mustInRange，返回 YES 且更新数据库
      (with-redefs [inspur/local-time #(LocalTime/of 10 0)]
        (let [now (inspur/local-time)
              r1start (.minusSeconds now 100)
              r1end (.plusSeconds now 100)
              check (let [t (.plusSeconds now 50)]
                      (format "%s:%s" (.getHour t) (.getMinute t)))]
          (with-redefs [db/get-today-auto (fn [& _] {:r1start r1start
                                                     :r1end   r1end
                                                     :r2start (LocalTime/of 17 30)
                                                     :r2end   (LocalTime/of 18 30)
                                                     :info    {}})
                        db/update-auto-info #(swap! a assoc :auto (:info %))]
            (let [_ (clean!)
                  message (inspur/handle-serve-auto {:needCheckAt check
                                                     :mustInRange false})
                  {:keys [status cost]} (-> @a :auto :check first)]
              (is (= message "YES"))
              (is (not (nil? (:auto @a))))
              (is (= status "ready!"))
              (is (= cost 600)))))))

    (testing "handle serve auto - save request as check to database (in range and now in range)"
      ;在策略时间请求，需要 mustInRange，则返回 YES 且更新数据库
      (with-redefs [inspur/local-time #(LocalTime/of 10 0)]
        (let [now (inspur/local-time)
              r1start (.minusSeconds now 100)
              r1end (.plusSeconds now 100)
              check (let [t (.plusSeconds now 50)]
                      (format "%s:%s" (.getHour t) (.getMinute t)))]
          (with-redefs [db/get-today-auto (fn [& _] {:r1start r1start
                                                     :r1end   r1end
                                                     :r2start (LocalTime/of 17 30)
                                                     :r2end   (LocalTime/of 18 30)
                                                     :info    {}})
                        db/update-auto-info #(swap! a assoc :auto (:info %))]
            (let [_ (clean!)
                  message (inspur/handle-serve-auto {:needCheckAt check
                                                     :mustInRange true})
                  {:keys [status cost]} (-> @a :auto :check first)]
              (is (= message "YES"))
              (is (not (nil? (:auto @a))))
              (is (= status "ready!"))
              (is (= cost 600)))))))

    (testing "handle serve auto - save request as check to database (not in range)"
      ;在策略时间 前 请求，不需要 mustInRange，返回 YES 且不更新数据库
      (with-redefs [inspur/local-time #(LocalTime/of 10 0)]
        (let [now (inspur/local-time)
              r1start (.plusMinutes now 1)
              r1end (.plusMinutes now 100)
              check (let [t (.plusMinutes now 3)]
                      (format "%s:%s" (.getHour t) (.getMinute t)))]
          (with-redefs [db/get-today-auto (fn [& _] {:r1start r1start
                                                     :r1end   r1end
                                                     :r2start (LocalTime/of 17 30)
                                                     :r2end   (LocalTime/of 18 30)
                                                     :info    {}})
                        db/update-auto-info #(swap! a assoc :auto (:info %))]
            (let [_ (clean!)
                  message (inspur/handle-serve-auto {:needCheckAt check
                                                     :mustInRange false})
                  {:keys [status cost]} (-> @a :auto :check first)]
              (is (= message "YES"))
              (is (nil? (:auto @a)))
              (is (= status nil))
              (is (= cost nil)))))))

    (testing "handle serve auto - save request as check to database
    (in range but not Now in range and mustInRange is false)"
      ;在策略时间 后 请求，不需要 mustInRange，则返回 YES 且不更新数据库
      (with-redefs [inspur/local-time #(LocalTime/of 20 0)]
        (let [lt10 (LocalTime/of 10 0)
              r1start (.minusSeconds lt10 100)
              r1end (.plusSeconds lt10 100)
              check (let [t (.plusSeconds lt10 50)]
                      (format "%s:%s" (.getHour t) (.getMinute t)))]
          (with-redefs [db/get-today-auto (fn [& _] {:r1start r1start
                                                     :r1end   r1end
                                                     :r2start (LocalTime/of 17 30)
                                                     :r2end   (LocalTime/of 18 30)
                                                     :info    {}})
                        db/update-auto-info #(swap! a assoc :auto (:info %))]
            (let [_ (clean!)
                  message (inspur/handle-serve-auto {:needCheckAt check
                                                     :mustInRange false})
                  {:keys [status cost]} (-> @a :auto :check first)]
              (is (= message "YES"))
              (is (nil? (:auto @a))))))))

    (testing "handle serve auto - save request as check to database
    (in range but not Now in range and mustInRange is true)"
      ;在非策略时间请求，需要 mustInRange，则返回 NO 且不更新数据库
      (with-redefs [inspur/local-time #(LocalTime/of 20 0)]
        (let [lt10 (LocalTime/of 10 0)
              r1start (.minusSeconds lt10 100)
              r1end (.plusSeconds lt10 100)
              check (let [t (.plusSeconds lt10 50)]
                      (format "%s:%s" (.getHour t) (.getMinute t)))]
          (with-redefs [db/get-today-auto (fn [& _] {:r1start r1start
                                                     :r1end   r1end
                                                     :r2start (LocalTime/of 17 30)
                                                     :r2end   (LocalTime/of 18 30)
                                                     :info    {}})
                        db/update-auto-info #(swap! a assoc :auto (:info %))]
            (let [_ (clean!)
                  message (inspur/handle-serve-auto {:needCheckAt check
                                                     :mustInRange true})
                  {:keys [status cost]} (-> @a :auto :check first)]
              (is (= message "NO"))
              (is (nil? (:auto @a))))))))

    (testing "handle serve auto - save request as check to database
    (in range but not Now in range and mustInRange is true by default)"
      ;在非策略时间请求，需要 mustInRange，则返回 NO 且不更新数据库（默认参数）
      (with-redefs [inspur/local-time #(LocalTime/of 20 0)]
        (let [lt10 (LocalTime/of 10 0)
              r1start (.minusSeconds lt10 100)
              r1end (.plusSeconds lt10 100)
              check (let [t (.plusSeconds lt10 50)]
                      (format "%s:%s" (.getHour t) (.getMinute t)))]
          (with-redefs [db/get-today-auto (fn [& _] {:r1start r1start
                                                     :r1end   r1end
                                                     :r2start (LocalTime/of 17 30)
                                                     :r2end   (LocalTime/of 18 30)
                                                     :info    {}})
                        db/update-auto-info #(swap! a assoc :auto (:info %))]
            (let [_ (clean!)
                  message (inspur/handle-serve-auto {:needCheckAt check})
                  {:keys [status cost]} (-> @a :auto :check first)]
              (is (= message "NO"))
              (is (nil? (:auto @a))))))))))

(deftest today-auto-info-check
  (testing "auto-today-info-check with nothing checked"
    ;早晨自动检查，当前时间早过策略开始时间，虽然没有 check 也直接跳过
    (let [a (atom {})
          s #(swap! a assoc %1 %2)
          y #(= (get @a %1) %2)
          n #(not= (get @a %1) %2)]
      (with-redefs [inspur/local-time #(LocalTime/of 10 0)
                    inspur/local-date-time #(LocalDateTime/of 2022 03 03 10 0)
                    inspur/local-date #(LocalDate/of 2022 03 03)
                    inspur/get-hcm-info (fn [& _] (s :get-hcm :true) [])
                    inspur/signin-data (fn [& _] (s :signin-data :true) [])
                    inspur/signin-hint (fn [& _] (s :signin-hint :true)
                                         {:needMorningCheck true
                                          :offWork          false})
                    slack/notify (fn [& _] (s :notice :true))]
        (let [now (inspur/local-time)
              now-dt (inspur/local-date-time)
              r1start (.plusMinutes now 1)
              r1end (.plusMinutes now 100)]
          (with-redefs [db/get-today-auto
                        (fn [& _]
                          (s :fetch-today :true)
                          {:r1start r1start
                           :r1end   r1end
                           :r2start (LocalTime/of 17 30)
                           :r2end   (LocalTime/of 18 30)
                           :info    {:check []
                                     :mark-morning-failed nil
                                     :mark-night-failed   nil}})
                        db/update-auto-info (fn [{:keys [info]}]
                                              (s :update-info
                                                 (-> info :check first :status)))]
            (let [_ (inspur/auto-today-info-check!)
                  _ (println @a)]
              (is (y :fetch-today :true))
              (is (nil? (::get-hcm @a)))
              (is (nil? (:signin-data @a)))
              (is (nil? (:signin-hint @a)))
              (is (nil? (:update-info @a)))))))))

  (testing "auto-today-info-check with morning need check not end"
    ;早晨自动检查，当前 check 还未完毕，跳过
    (let [a (atom {})
          s #(swap! a assoc %1 %2)
          y #(= (get @a %1) %2)
          n #(not= (get @a %1) %2)]
      (with-redefs [inspur/local-time #(LocalTime/of 10 0)
                    inspur/local-date-time #(LocalDateTime/of 2022 03 03 10 0)
                    inspur/local-date #(LocalDate/of 2022 03 03)
                    inspur/get-hcm-info (fn [& _] (s :get-hcm :true) [])
                    inspur/signin-data (fn [& _] (s :signin-data :true) [])
                    inspur/signin-hint (fn [& _] (s :signin-hint :true)
                                         {:needMorningCheck false
                                          :offWork          false})
                    slack/notify (fn [& _] (s :notice :true))]
        (let [now (inspur/local-time)
              now-dt (inspur/local-date-time)
              r1start (.plusMinutes now 1)
              r1end (.plusMinutes now 100)]
          (with-redefs [db/get-today-auto
                        (fn [& _]
                          (s :fetch-today :true)
                          {:r1start r1start
                           :r1end   r1end
                           :r2start (LocalTime/of 17 30)
                           :r2end   (LocalTime/of 18 30)
                           :info    {:check
                                     [{:cost   600
                                       :start  (str (.minusSeconds now-dt 100))
                                       :status "ready!"}]
                                     :mark-morning-failed nil
                                     :mark-night-failed   nil}})
                        db/update-auto-info #(swap! a assoc :auto (:info %))]
            (let [_ (inspur/auto-today-info-check!)
                  _ (println @a)]
              (is (y :fetch-today :true))
              (is (n :get-hcm :true))
              (is (n :signin-data :true))
              (is (n :signin-hint :true))))))))

  (testing "auto-today-info-check with morning need check finished hcm success"
    ;早晨自动检查，当前策略已完毕，且成功，没有动作
    (let [a (atom {})
          s #(swap! a assoc %1 %2)
          y #(= (get @a %1) %2)
          n #(not= (get @a %1) %2)]
      (with-redefs [inspur/local-time #(LocalTime/of 10 0)
                    inspur/local-date-time #(LocalDateTime/of 2022 03 03 10 0)
                    inspur/local-date #(LocalDate/of 2022 03 03)
                    inspur/get-hcm-info (fn [& _] (s :get-hcm :true) [])
                    inspur/signin-data (fn [& _] (s :signin-data :true) [])
                    inspur/signin-hint (fn [& _] (s :signin-hint :true)
                                         {:needMorningCheck false
                                          :offWork          false})
                    slack/notify (fn [& _] (s :notice :true))]
        (let [now (inspur/local-time)
              now-dt (inspur/local-date-time)
              r1start (.plusMinutes now 1)
              r1end (.plusMinutes now 100)]
          (with-redefs [db/get-today-auto
                        (fn [& _]
                          (s :fetch-today :true)
                          {:r1start r1start
                           :r1end   r1end
                           :r2start (LocalTime/of 17 30)
                           :r2end   (LocalTime/of 18 30)
                           :info    {:check
                                     [{:cost   600
                                       :start  (str (.minusSeconds now-dt 700))
                                       :status "ready!"}]
                                     :mark-morning-failed nil
                                     :mark-night-failed   nil}})
                        db/update-auto-info (fn [{:keys [info]}]
                                              (s :update-info
                                                 (-> info :check first :status)))]
            (let [_ (inspur/auto-today-info-check!)
                  _ (println @a)]
              (is (y :fetch-today :true))
              (is (y :get-hcm :true))
              (is (y :signin-data :true))
              (is (y :signin-hint :true))
              (is (n :notice :true))
              (is (y :update-info "done!"))))))))

  (testing "auto-today-info-check with morning need check finished hcm failed"
    ;早晨自动检查，当前策略已完毕，但失败，通知并更新数据库 check 标记
    (let [a (atom {})
          s #(swap! a assoc %1 %2)
          y #(= (get @a %1) %2)
          n #(not= (get @a %1) %2)]
      (with-redefs [inspur/local-time #(LocalTime/of 10 0)
                    inspur/local-date-time #(LocalDateTime/of 2022 03 03 10 0)
                    inspur/local-date #(LocalDate/of 2022 03 03)
                    inspur/get-hcm-info (fn [& _] (s :get-hcm :true) [])
                    inspur/signin-data (fn [& _] (s :signin-data :true) [])
                    inspur/signin-hint (fn [& _] (s :signin-hint :true)
                                         {:needMorningCheck true
                                          :offWork          false})
                    slack/notify (fn [& _] (s :notice :true))]
        (let [now (inspur/local-time)
              now-dt (inspur/local-date-time)
              r1start (.plusMinutes now 1)
              r1end (.plusMinutes now 100)]
          (with-redefs [db/get-today-auto
                        (fn [& _]
                          (s :fetch-today :true)
                          {:r1start r1start
                           :r1end   r1end
                           :r2start (LocalTime/of 17 30)
                           :r2end   (LocalTime/of 18 30)
                           :info    {:check
                                     [{:cost   600
                                       :start  (str (.minusSeconds now-dt 700))
                                       :status "ready!"}]
                                     :mark-morning-failed nil
                                     :mark-night-failed   nil}})
                        db/update-auto-info (fn [{:keys [info]}]
                                              (s :update-info
                                                 (-> info :check first :status)))]
            (let [_ (inspur/auto-today-info-check!)
                  _ (println @a)]
              (is (y :fetch-today :true))
              (is (y :get-hcm :true))
              (is (y :signin-data :true))
              (is (y :signin-hint :true))
              (is (y :notice :true))
              (is (y :update-info "failed!"))))))))

  (testing "auto-today-info-check with everything checked"
    ;早晨有多个 check，其都不需要检查：没有 ready！ 标记，则跳过
    (let [a (atom {})
          s #(swap! a assoc %1 %2)
          y #(= (get @a %1) %2)
          n #(not= (get @a %1) %2)]
      (with-redefs [inspur/local-time #(LocalTime/of 10 0)
                    inspur/local-date-time #(LocalDateTime/of 2022 03 03 10 0)
                    inspur/local-date #(LocalDate/of 2022 03 03)
                    inspur/get-hcm-info (fn [& _] (s :get-hcm :true) [])
                    inspur/signin-data (fn [& _] (s :signin-data :true) [])
                    inspur/signin-hint (fn [& _] (s :signin-hint :true)
                                         {:needMorningCheck true
                                          :offWork          false})
                    slack/notify (fn [& _] (s :notice :true))]
        (let [now (inspur/local-time)
              now-dt (inspur/local-date-time)
              r1start (.plusMinutes now 1)
              r1end (.plusMinutes now 100)]
          (with-redefs [db/get-today-auto
                        (fn [& _]
                          (s :fetch-today :true)
                          {:r1start r1start
                           :r1end   r1end
                           :r2start (LocalTime/of 17 30)
                           :r2end   (LocalTime/of 18 30)
                           :info    {:check [{:cost   600
                                              :start  (str (.minusSeconds now-dt 700))
                                              :status "failed!"}
                                             {:cost   600
                                              :start  (str (.minusSeconds now-dt 700))
                                              :status "done!"}
                                             {:cost   600
                                              :start  (str (.minusSeconds now-dt 700))
                                              :status "failed!"}]
                                     :mark-morning-failed nil
                                     :mark-night-failed   nil}})
                        db/update-auto-info (fn [{:keys [info]}]
                                              (s :update-info
                                                 (-> info :check first :status)))]
            (let [_ (inspur/auto-today-info-check!)
                  _ (println @a)]
              (is (y :fetch-today :true))
              (is (nil? (::get-hcm @a)))
              (is (nil? (:signin-data @a)))
              (is (nil? (:signin-hint @a)))
              (is (nil? (:update-info @a)))))))))

  (testing "auto-today-info-check morning no check"
    ;早晨没有检查，且超过了策略最大时间，通知并更新数据库
    (let [a (atom {})
          s #(swap! a assoc %1 %2)
          y #(= (get @a %1) %2)
          n #(not= (get @a %1) %2)]
      (with-redefs [inspur/local-time #(LocalTime/of 10 0)
                    inspur/local-date-time #(LocalDateTime/of 2022 03 03 10 0)
                    inspur/local-date #(LocalDate/of 2022 03 03)
                    inspur/get-hcm-info (fn [& _] (s :get-hcm :true) [])
                    inspur/signin-data (fn [& _] (s :signin-data :true) [])
                    inspur/signin-hint (fn [& _] (s :signin-hint :true)
                                         {:needMorningCheck true
                                          :offWork          false})
                    slack/notify (fn [m _] (s :notice m))]
        (let [now (inspur/local-time)
              now-dt (inspur/local-date-time)
              r1start (.minusSeconds now 500)
              r1end (.minusSeconds now 100)]
          (with-redefs [db/get-today-auto
                        (fn [& _]
                          (s :fetch-today :true)
                          {:r1start r1start
                           :r1end   r1end
                           :r2start (LocalTime/of 17 30)
                           :r2end   (LocalTime/of 18 30)
                           :info    {:check []
                                     :mark-morning-failed nil
                                     :mark-night-failed   nil}})
                        db/update-auto-info (fn [{:keys [info]}]
                                              (s :update-info
                                                 (-> info :check first :status))
                                              (s :update-info-mmf
                                                 (-> info :mark-morning-failed))
                                              (s :update-info-mnf
                                                 (-> info :mark-night-failed)))]
            (let [_ (inspur/auto-today-info-check!)
                  _ (println @a)]
              (is (y :fetch-today :true))
              (is (nil? (::get-hcm @a)))
              (is (nil? (:signin-data @a)))
              (is (nil? (:signin-hint @a)))
              (is (nil? (:update-info @a)))
              (is (str/includes? (:notice @a) "记录了策略，但是早上"))
              (is (= (:update-info-mmf @a) true))
              (is (= (:update-info-mnf @a) nil))))))))

  (testing "auto-today-info-check night no check"
    ;晚上没有检查，超过了策略最大事件，更新数据库并通知
    (let [a (atom {})
          s #(swap! a assoc %1 %2)
          y #(= (get @a %1) %2)
          n #(not= (get @a %1) %2)]
      (with-redefs [inspur/local-time #(LocalTime/of 19 0)
                    inspur/local-date-time #(LocalDateTime/of 2022 03 03 19 0)
                    inspur/local-date #(LocalDate/of 2022 03 03)
                    inspur/get-hcm-info (fn [& _] (s :get-hcm :true) [])
                    inspur/signin-data (fn [& _] (s :signin-data :true) [])
                    inspur/signin-hint (fn [& _] (s :signin-hint :true)
                                         {:needMorningCheck true
                                          :offWork          false})
                    slack/notify (fn [m _] (s :notice m))]
        (let [now (inspur/local-time)
              now-dt (inspur/local-date-time)
              r1start (.minusSeconds now 500)
              r1end (.minusSeconds now 100)]
          (with-redefs [db/get-today-auto
                        (fn [& _]
                          (s :fetch-today :true)
                          {:r1start r1start
                           :r1end   r1end
                           :r2start (LocalTime/of 17 30)
                           :r2end   (LocalTime/of 18 30)
                           :info    {:check []
                                     :mark-morning-failed nil
                                     :mark-night-failed   nil}})
                        db/update-auto-info (fn [{:keys [info]}]
                                              (s :update-info
                                                 (-> info :check first :status))
                                              (s :update-info-mmf
                                                 (-> info :mark-morning-failed))
                                              (s :update-info-mnf
                                                 (-> info :mark-night-failed)))]
            (let [_ (inspur/auto-today-info-check!)
                  _ (println @a)]
              (is (y :fetch-today :true))
              (is (nil? (::get-hcm @a)))
              (is (nil? (:signin-data @a)))
              (is (nil? (:signin-hint @a)))
              (is (nil? (:update-info @a)))
              (is (str/includes? (or (:notice @a) "") "记录了策略，但是晚上"))
              (is (= (:update-info-mmf @a) nil))
              (is (= (:update-info-mnf @a) true)))))))))