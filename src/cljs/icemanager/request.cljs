(ns icemanager.request
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [reitit.frontend.easy :as rfe]
    [reitit.frontend.controllers :as rfc]
    [clojure.string :as string]))

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