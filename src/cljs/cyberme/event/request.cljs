(ns cyberme.event.request
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [clojure.string :as string]
    [clojure.set :as set]))

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
                        {:message  (str "请求调用失败：" (:last-error error))
                         :callback (vector data-clean)}]))
        (assoc db data
                  {:data    nil
                   :status  0
                   :message (str "请求调用失败：" (:last-error error))})))
    (rf/reg-sub data (fn [db _] (data db)))
    (rf/reg-event-db data-clean (fn [db _] (dissoc db data)))))

;;;;;;;;;;;;;;;;;;;;;;;; 位置 ;;;;;;;;;;;;;;;;;;;;;;
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
(ajax-flow {:call    :place/edit
            :data    :place/edit-callback
            :clean   :place/edit-callback-clean
            :uri-fn  #(str "/api/place/" (:id %))
            :is-post true})

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

;;;;;;;;;;;;;;;;;;;;;;;; 打包 ;;;;;;;;;;;;;;;;;;;;;;
;最近打包和位置，静默写入数据，失败弹窗
(ajax-flow {:call           :recent/fetch
            :uri-fn         #(str "/api/recent")
            :data           :recent/fetch-data
            :clean          :recent/fetch-data-clean
            :failure-notice true})

;新建打包，失败后由对话框获取数据展示并自行清除
(ajax-flow {:call    :package/new
            :is-post true
            :uri-fn  #(str "/api/package")
            :data    :package/new-failure
            :clean   :package/new-clean-failure})

;删除打包，成功和失败后均弹窗提示，成功还刷新主界面，触发打包重新获取
(ajax-flow {:call                   :package/delete
            :is-post                true
            :uri-fn                 #(str "/api/package/" % "/delete")
            :clean                  :package/delete-data-clean
            :success-notice         true
            :success-callback-event [[:package/fetch] [:place/fetch] [:package/delete-data-clean]]
            :failure-notice         true})

;;;;;;;;;;;;;;;;;;;;;;;; 物品 ;;;;;;;;;;;;;;;;;;;;;;
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
            :data                   :good/delete-data
            :clean                  :good/delete-data-clean
            :success-notice         true
            :success-callback-event [[:place/fetch] [:good/delete-data-clean]]
            :failure-notice         true})

;隐藏物品，数据库返回请求全部显示，如果成功还刷新主界面
(ajax-flow {:call                   :good/hide
            :is-post                true
            :uri-fn                 #(str "/api/good/" % "/hide")
            :data                   :good/hide-data
            :clean                  :good/hide-data-clean
            :success-notice         true
            :success-callback-event [[:place/fetch] [:good/hide-data-clean]]
            :failure-notice         true})

;物品更改位置，数据库返回请求全部显示，如果成功还刷新主界面
(ajax-flow {:call                   :good/move
            :is-post                true
            :data                   :good/move-data
            :clean                  :good/move-data-clean
            :uri-fn                 #(str "/api/good/" (first %) "/move/" (last %))
            :success-notice         true
            :success-callback-event [[:place/fetch] [:good/move-data-clean]]
            :failure-notice         true})

;物品取消打包，数据库返回结果不论成功失败全部显示，并刷新主界面
(ajax-flow {:call                   :good/unbox
            :uri-fn                 #(str "/api/good/" (first %) "/unbox/" (second %))
            :data                   :good/unbox-data
            :clean                  :good/unbox-data-clean
            :success-notice         true
            :success-callback-event [[:place/fetch] [:good/unbox-data-clean]]
            :failure-notice         true})

;物品确定打包，数据库返回结果失败显示，成功则刷新主界面
(ajax-flow {:call                   :good/box
            :uri-fn                 #(str "/api/good/" (first %) "/box/" (second %))
            :data                   :good/box-data
            :clean                  :good/box-data-clean
            :success-callback-event [[:place/fetch] [:good/box-data-clean]]
            :failure-notice         true})

;物品准备打包，数据库返回结果失败显示，成功则刷新主界面
(ajax-flow {:call                   :good/plan
            :uri-fn                 #(str "/api/good/" (first %) "/plan/" (second %))
            :data                   :good/plan-data
            :clean                  :good/plan-data-clean
            :success-callback-event [[:place/fetch] [:good/plan-data-clean]]
            :failure-notice         true})

;修改物品
(ajax-flow {:call    :good/edit
            :data    :good/edit-callback
            :clean   :good/edit-callback-clean
            :uri-fn  #(str "/api/good/" (:id %))
            :is-post true})

(rf/reg-event-db
  :good/current (fn [db [_ data]] (assoc db :good/current data)))

(rf/reg-sub
  :good/current (fn [db _] (:good/current db)))

(rf/reg-event-db
  :good/current-clean (fn [db _] (dissoc db :good/current)))

;;;;;;;;;;;;;;;;;;;;;;;;;;; hcm ;;;;;;;;;;;;;;;;;;;;;;;;;;;
(ajax-flow {:call :hcm/month
            :data :hcm/month-data
            :clean :hcm/month-data-clean
            :uri-fn #(str "/cyber/check/month_summary")
            :is-post false
            :failure-notice true})

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