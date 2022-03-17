(ns cyberme.util.request
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [clojure.string :as string]
    [goog.crypt.base64 :as b64]
    [clojure.set :as set]
    [cyberme.util.storage :as storage]
    [clojure.string :as str]))

(defn auth-header []
  (let [;{:keys [username password]} (storage/get-item "api_auth")
        api-auth (rf/subscribe [:api-auth])
        {username :user password :pass} @api-auth]
    (if (or (nil? username) (nil? password))
      {"Authorization" (str "Basic " (b64/encodeString (str "unknown" ":" "unknown")))}
      {"Authorization" (str "Basic " (b64/encodeString (str username ":" password)))})))

;;;;;;;;;;;;;; login ;;;;;;;;;;;;
(rf/reg-event-db
  :user/fetch-from-local
  (fn [db _]
    (let [{:keys [username password]} (storage/get-item "api_auth")]
      (when (and username password)
        (assoc db :api-auth {:user username :pass password})))))

(rf/reg-sub :api-auth (fn [db _] (:api-auth db)))

(rf/reg-event-db
  :show-login
  (fn [_ [_ _]]
    (rf/dispatch [:app/show-modal :login-info-set])))

(rf/reg-event-db
  :handle-all-failure
  (fn [_ [_ error]]
    (rf/dispatch [:global/notice
                  {:message  (let [error (or (:last-error error) (str error))]
                               (if (str/includes? error "Forbidden")
                                 (str "请求调用失败，没有权限，请点击“确定”登录后再试。")
                                 (str "请求调用失败：" error)))
                   :callback (if (str/includes? (str (:last-error error)) "403")
                               [:show-login]
                               [])}])))

;;;;;;;;;;;;;;;;; ajax-flow 抽象 ;;;;;;;;;;;;;;;;;
(defn ajax-flow [{:keys [call data clean uri-fn is-post
                         success-notice failure-notice
                         success-callback-event]}]
  "一套基于 Ajax 的事件传递方案：
  通过 ajax 触发：model/action ->
  ajax 回调设置： model/action-on-success[failure] ->
  更新数据库信息：model/action-data {data message status} ->
  提供弹窗通知(成功和失败）以及成功后的一个或多个事件回调，弹窗通知后会自动清空数据
  提供数据查询和清空数据动作：action-data & action-data-clean
  eg:
  (ajax-call {:call :package/all :uri-fn (fn [id] (str \"/hello/\" id)})
  (ajax-call {:call :package/new :uri-fn (fn [_] (str \"/hello/\") :is-post true})"
  (let [key-str (string/replace (str call) ":" "")          ;package/all
        model-key-list (string/split key-str "/")
        model (first model-key-list)                        ;package
        key (last model-key-list)                           ;all
        on-failure (keyword (str model "/" key "-on-failure")) ;package-all-on-failure
        on-success (keyword (str model "/" key "-on-success")) ;package-all-on-success
        data (or data (keyword (str model "/" key "-data"))) ;package-all-data
        data-clean (or clean (keyword (str model "/" key "-data-clean"))) ;package-all-data-clean
        success-callback-events
        (if (and (not (nil? success-callback-event))
                 (vector? success-callback-event)
                 (not-empty success-callback-event)
                 (vector? (first success-callback-event)))
          success-callback-event
          (vector success-callback-event))]
    (rf/reg-event-fx
      call
      (fn [_ [_ data]]
        (if-not is-post
          {:http-xhrio {:method          :get
                        :headers         (auth-header)
                        :uri             (uri-fn data)
                        :response-format (ajax/json-response-format {:keywords? true})
                        :on-failure      [on-failure]
                        :on-success      [on-success]}}
          {:http-xhrio {:method          :post
                        :headers         (auth-header)
                        :params          data
                        :uri             (uri-fn data)
                        :format          (ajax/json-request-format)
                        :response-format (ajax/json-response-format {:keywords? true})
                        :on-failure      [on-failure]
                        :on-success      [on-success]}})))
    (rf/reg-event-db
      on-success
      (fn [db [_ resp]]                                     ;data, message, status
        (when (= (:status resp) 1)
          (if success-notice
            (rf/dispatch [:global/notice
                          {:message  (or (:message resp)
                                         "服务器成功响应，但无回调消息。")
                           :callback success-callback-event}])
            (when success-callback-event
              (doseq [sce success-callback-events]
                (rf/dispatch (vec sce))))))
        (when (and failure-notice (= (:status resp) 0))
          (rf/dispatch [:global/notice
                        {:message (or (:message resp)
                                      "服务器返回了一个错误。")}]))
        (assoc db data resp)))
    (rf/reg-event-db
      on-failure
      (fn [db [_ error]]
        (when failure-notice
          (rf/dispatch [:global/notice
                        {:message  (let [error (or (:last-error error) (str error))]
                                     (if (str/includes? error "Forbidden")
                                       (str "请求调用失败，没有权限，请点击“确定”登录后再试。")
                                       (str "请求调用失败：" error)))
                         :callback (if (str/includes? (str (:last-error error)) "403")
                                     [:show-login]
                                     (vector data-clean))}]))
        (assoc db data
                  {:data    nil
                   :status  0
                   :message (str "请求调用失败：" (:last-error error))})))
    (rf/reg-sub data (fn [db _] (data db)))
    (rf/reg-event-db data-clean (fn [db _] (dissoc db data)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;; notice ;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;dispatch [:global/notice {:message :callback (may nil)}]
;show modal with :message
;click ok call :callback event if necessary

(rf/reg-event-db
  :global/notice
  (fn [db [_ data]]
    (rf/dispatch [:app/show-modal :notice])
    (assoc db :global/notice data)))

(rf/reg-event-db
  :global/notice-clean
  (fn [db _]
    (dissoc db :global/notice)))

(rf/reg-sub
  :global/notice
  (fn [db _]
    (:global/notice db)))


;;;;;;;;;;;;;;;;;;;;;;;;;;; filter ;;;;;;;;;;;;;;;;;;;;;;;;;;
(rf/reg-sub
  :all-location
  (fn [db _]
    (set (map #(get % :location) (get-in db [:place/fetch-data :data])))))

(rf/reg-sub
  :all-label
  (fn [db _]
    (let [data-list (get-in db [:place/fetch-data :data])
          item-list (map #(:items %) data-list)
          item-list (flatten1 item-list)
          labels-set (set (filter (comp not nil?) (flatten (map #(:labels %) item-list))))]
      labels-set)))

(rf/reg-sub
  :all-status
  (fn [db _]
    (let [data-list (get-in db [:place/fetch-data :data])
          item-list (map #(:items %) data-list)
          item-list (flatten1 item-list)
          status-set (set (map #(:status %) item-list))]
      status-set)))


(rf/reg-event-db
  :set-filter
  (fn [db [_ data]]
    (assoc db :filter data)))

(rf/reg-sub
  :filter
  (fn [db _] (:filter db)))

(rf/reg-sub
  :place/fetch-data-filtered
  (fn [db _]
    (let [{:keys [status labels location search]} (:filter db)
          labels (set (cond (nil? labels) (set [])
                            (vector? labels) (set labels)
                            :else (set (vector labels))))
          all-place (get-in db [:place/fetch-data :data])
          select-location-place
          (filter (fn [place] (if (string/blank? location)
                                true
                                (= (:location place) location)))
                  all-place)
          right-status-fn #(or (string/blank? status)
                               (= (:status %) status))
          right-labels-fn (fn [item]
                            (empty? (set/difference labels (set (:labels item)))))
          fit-search-fn #(or (string/blank? search)
                             (string/includes? (:name %) search))
          right-s-l-place
          (map (fn [place]
                 (assoc place
                   :items
                   (filter #(and (right-labels-fn %)
                                 (right-status-fn %)
                                 (fit-search-fn %))
                           (:items place))))
               select-location-place)
          hide-no-item-place (if (and (nil? status)
                                      (empty? labels)
                                      (string/blank? search))
                               right-s-l-place
                               (filter #(not-empty (:items %)) right-s-l-place))]
      hide-no-item-place)))

;;;;;;;;;;;;;; usage ;;;;;;;;;;;

(rf/reg-event-fx
  :fetch-usage
  (fn [_ _]
    {:http-xhrio {:method          :get
                  :headers         (auth-header)
                  :uri             "/api/usage"
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [:set-usage]
                  :on-failure      [:handle-all-failure]}}))

(rf/reg-event-db
  :set-usage
  (fn [db [_ feature]]
    (assoc db :usage feature)))

(rf/reg-sub
  :usage
  (fn [db _]
    (:usage db)))

;;;;;;;;;;;;;; wishlist ;;;;;;;;;;;

(rf/reg-event-fx
  :send-wishlist
  (fn [_ [_ data]]
    {:http-xhrio {:method          :post
                  :headers         (auth-header)
                  :params          data
                  :uri             (str "/api/wishlist")
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-failure      [:set-send-wishlist-error]
                  :on-success      [:set-send-wishlist-success]}}))

(rf/reg-event-db
  :set-send-wishlist-success
  (fn [db [_ result]]
    (assoc db :wishlist-server-back {:status  :success
                                     :content "请求已记录，感谢您的反馈。"})))

(rf/reg-event-db
  :set-send-wishlist-error
  (fn [db [_ error]]
    (rf/dispatch [:handle-all-failure])
    (assoc db :wishlist-server-back {:status  :fail
                                     :content error})))

(rf/reg-event-db
  :clean-wishlist-server-back
  (fn [db _]
    (dissoc db :wishlist-server-back)))

(rf/reg-sub
  :wishlist-server-back
  (fn [db _]
    (:wishlist-server-back db)))

(rf/reg-event-fx
  :fetch-wishlist
  (fn [_ _]
    {:http-xhrio {:method          :get
                  :headers         (auth-header)
                  :uri             "/api/wishlist"
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [:set-wishlist]
                  :on-failure      [:handle-all-failure]}}))

(rf/reg-event-db
  :set-wishlist
  (fn [db [_ wishlist]]
    (assoc db :wishlist wishlist)))

(rf/reg-sub
  :wishlist
  (fn [db _]
    (:wishlist db)))