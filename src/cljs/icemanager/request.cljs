(ns icemanager.request
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [clojure.string :as string]))

(defn ajax-flow [{:keys [call data clean uri-fn is-post
                         success-notice failure-notice
                         success-callback-event]}]
  "一套基于 Ajax 的事件传递方案：
  通过 ajax 触发：model/action ->
  ajax 回调设置： model/action-on-success[failure] ->
  更新数据库信息：model/action-data {data message status} ->
  提供弹窗通知(成功和失败）：
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
        ]
    (rf/reg-event-fx
      call
      (fn [_ [_ data]]
        (if-not is-post
          {:http-xhrio {:method          :get
                        :uri             (uri-fn data)
                        :response-format (ajax/json-response-format {:keywords? true})
                        :on-failure      [on-failure]
                        :on-success      [on-success]}}
          {:http-xhrio {:method          :post
                        :params          data
                        :uri             (uri-fn data)
                        :format          (ajax/json-request-format)
                        :response-format (ajax/json-response-format {:keywords? true})
                        :on-failure      [on-failure]
                        :on-success      [on-success]}})))
    (rf/reg-event-db
      on-success
      (fn [db [_ resp]]                                     ;data, message, status
        (when (and success-notice (= (:status resp) 1))
          (rf/dispatch [:global/notice
                        {:message  (or (:message resp)
                                       "服务器成功响应，但无回调消息。")
                         :callback success-callback-event}]))
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
                        {:message  (str "请求调用失败：" (:last-error error))
                         :callback (vector data-clean)}]))
        (assoc db data
                  {:data    nil
                   :status  0
                   :message (str "请求调用失败：" (:last-error error))})))
    (rf/reg-sub data (fn [db _] (data db)))
    (rf/reg-event-db data-clean (fn [db _] (dissoc db data)))))

;;;;;;;;;;;;;; 业务事件 ;;;;;;;;;;;;
;获取所有位置和物品数据，失败后自动弹出对话框并清空数据
(ajax-flow {:call           :place/fetch
            :uri-fn         #(str "/api/places")
            :failure-notice true
            :data           :place/fetch-data
            :clean          :place/clean-data})

;新建位置，失败后由对话框获取数据展示并自行清除
(ajax-flow {:call    :place/new
            :is-post true
            :uri-fn  #(str "/api/place")
            :data    :place/new-failure
            :clean   :place/new-clean-failure})

;修改位置
(ajax-flow {:call                   :place/edit
            :data                   :place/edit-callback
            :clean                  :place/edit-callback-clean
            :uri-fn                 #(str "/api/place/" (:id %))
            :is-post                true})

(rf/reg-event-db
  :place/current (fn [db [_ data]] (assoc db :place/current data)))

(rf/reg-sub
  :place/current (fn [db _] (:place/current db)))

(rf/reg-event-db
  :place/current-clean (fn [db _] (dissoc db :place/current)))

;删除位置，数据库返回请求全部显示，如果成功还刷新主界面
(ajax-flow {:call                   :place/delete
            :is-post                true
            :uri-fn                 #(str "/api/place/" % "/delete")
            :success-notice         true
            :success-callback-event [:place/fetch]
            :failure-notice         true})

;新建打包，失败后由对话框获取数据展示并自行清除
(ajax-flow {:call    :package/new
            :is-post true
            :uri-fn  #(str "/api/package")
            :data    :package/new-failure
            :clean   :package/new-clean-failure})

;新建物品，失败后由对话框获取数据展示并自行清除
(ajax-flow {:call    :good/new
            :is-post true
            :uri-fn  #(str "/api/good")
            :data    :good/new-failure
            :clean   :good/new-clean-failure})

;删除物品，数据库返回请求全部显示，如果成功还刷新主界面
(ajax-flow {:call                   :good/delete
            :is-post                true
            :uri-fn                 #(str "/api/good/" % "/delete")
            :success-notice         true
            :success-callback-event [:place/fetch]
            :failure-notice         true})

;隐藏物品，数据库返回请求全部显示，如果成功还刷新主界面
(ajax-flow {:call                   :good/hide
            :is-post                true
            :uri-fn                 #(str "/api/good/" % "/hide")
            :success-notice         true
            :success-callback-event [:place/fetch]
            :failure-notice         true})


;;;; dispatch [:global/notice {:message :callback (may nil)}]
;;;; show modal with :message
;;;; click ok call :callback event if necessary

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

;;;;;;;;;;;;;; fetch feature and features, update feature ;;;;;;;;;;;

(rf/reg-event-fx
  :fetch-features
  (fn [_ _]
    {:http-xhrio {:method          :get
                  :uri             "/api/features"
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [:set-features]}}))

(rf/reg-event-db
  :set-features
  (fn [db [_ features]]
    (assoc db :features features)))

(rf/reg-sub
  :get-features
  (fn [db _]
    (:features db)))

(rf/reg-event-db
  :set-filter
  (fn [db [_ data]]
    (assoc db :filter data)))

(rf/reg-sub
  :filter
  (fn [db _] (:filter db)))

(rf/reg-sub
  :get-filtered-features
  (fn [db _]
    (let [{:keys [version status contains]} (:filter db)]
      (vec (filter (fn [f] (let [ver (:version f)
                                 sta (-> f :info :status)
                                 all-dev (-> f :info :developer)
                                 all-dev (if (vector? all-dev) all-dev [all-dev])
                                 dev (set (map :name all-dev))]
                             (and (or (= version nil) (= version ver))
                                  (or (= status nil) (= status sta))
                                  (or (= contains nil) (contains? dev contains)))))
                   (:features db))))))

(rf/reg-sub
  :all-version
  (fn [db _]
    (set (map #(get % :version) (get db :features [])))))

(rf/reg-sub
  :all-status
  (fn [db _]
    (set (map #(get-in % [:info :status]) (get db :features [])))))

(rf/reg-sub
  :all-developer
  (fn [db _]
    (set (remove nil?
                 (flatten
                   (map (fn [{{:keys [developer]} :info}]
                          (map #(get % :name) developer))
                        (get db :features [])))))))

(rf/reg-event-fx
  :fetch-feature
  (fn [_ [_ rs-id-lower]]
    {:http-xhrio {:method          :get
                  :uri             (str "/api/feature/" rs-id-lower)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [:set-feature]}}))

(rf/reg-event-fx
  :update-feature
  (fn [_ [_ id data]]
    {:http-xhrio {:method          :post
                  :params          data
                  :uri             (str "/api/feature/" id)
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-failure      [:set-update-feature-error]
                  :on-success      [:set-update-feature-success]}}))

(rf/reg-event-db
  :set-feature
  (fn [db [_ feature]]
    (assoc db :current-feature feature)))

(rf/reg-event-db
  :set-update-feature-success
  (fn [db [_ feature]]
    (rf/dispatch [:app/show-modal :update-feature-notice])
    (assoc db :current-feature feature)))

(rf/reg-event-db
  :set-update-feature-error
  (fn [db [_ error]]
    (rf/dispatch [:app/show-modal :update-feature-notice])
    (assoc db :update-feature-error error)))

(rf/reg-sub
  :current-feature
  (fn [db _]
    (:current-feature db)))

(rf/reg-event-db
  :clean-current-feature
  (fn [db _]
    (dissoc db :current-feature)))

(rf/reg-sub
  :update-feature-error
  (fn [db _]
    (:update-feature-error db)))

;;;;;;;;;;;;;; usage ;;;;;;;;;;;

(rf/reg-event-fx
  :fetch-usage
  (fn [_ _]
    {:http-xhrio {:method          :get
                  :uri             "/api/usage"
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [:set-usage]}}))

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
                  :uri             "/api/wishlist"
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [:set-wishlist]}}))

(rf/reg-event-db
  :set-wishlist
  (fn [db [_ wishlist]]
    (assoc db :wishlist wishlist)))

(rf/reg-sub
  :wishlist
  (fn [db _]
    (:wishlist db)))

;;;;;;;;;;;;;; add feature ;;;;;;;;;;;

(rf/reg-event-fx
  :add-feature
  (fn [_ [_ data]]
    {:http-xhrio {:method          :post
                  :params          data
                  :uri             (str "/api/feature")
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-failure      [:set-add-feature-error]
                  :on-success      [:set-add-feature-success]}}))

(rf/reg-event-db
  :set-add-feature-success
  (fn [db [_ feature]]
    (assoc db :add-feature-server-back
              {:status  (if (:data feature) :success :fail)
               :content (:message feature)})))

(rf/reg-event-db
  :set-add-feature-error
  (fn [db [_ error]]
    (assoc db :add-feature-server-back
              {:status  :fail
               :content (str "新建特性失败：" error)})))

(rf/reg-event-db
  :clean-add-feature-server-back
  (fn [db _]
    (dissoc db :add-feature-server-back)))

(rf/reg-sub
  :add-feature-server-back
  (fn [db _]
    (:add-feature-server-back db)))

;;;;;;;;;;;;;; delete feature ;;;;;;;;;;;

(rf/reg-event-fx
  :delete-feature
  (fn [_ [_ id]]
    {:http-xhrio {:method          :post
                  :uri             (str "/api/feature/" id "/delete")
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-failure      [:set-del-feature-error]
                  :on-success      [:set-del-feature-success]}}))

(rf/reg-event-db
  :set-del-feature-success
  (fn [db [_ return]]
    (rf/dispatch [:app/show-modal :delete-feature-notice])
    (assoc db :del-feature-server-back
              {:status  (if (:data return) :success :fail)
               :content (:message return)})))

(rf/reg-event-db
  :set-del-feature-error
  (fn [db [_ error]]
    (rf/dispatch [:app/show-modal :delete-feature-notice])
    (assoc db :del-feature-server-back
              {:status  :fail
               :content (str "删除失败： " error)})))

(rf/reg-event-db
  :clean-del-feature-server-back
  (fn [db _]
    (dissoc db :del-feature-server-back)))

(rf/reg-sub
  :del-feature-server-back
  (fn [db _]
    (:del-feature-server-back db)))

(rf/reg-event-db
  :set-view-go
  (fn [db [_ go]]
    (assoc db :view-go go)))

(rf/reg-sub
  :view-go
  (fn [db _] (:view-go db)))

(rf/reg-event-db
  :clean-view-go
  (fn [db _]
    (dissoc db :view-go)))

;;;;;;;;;;;;;;;;;;
(rf/reg-sub
  :add-place-server-back
  (fn [db _]
    (:add-place-server-back db)))

(rf/reg-sub
  :add-package-server-back
  (fn [db _]
    (:add-package-server-back db)))

(rf/reg-sub
  :add-good-server-back
  (fn [db _]
    (:add-good-server-back db)))