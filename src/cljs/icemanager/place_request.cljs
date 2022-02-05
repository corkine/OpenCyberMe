(ns icemanager.place-request
  (:require [re-frame.core :as rf]
            [ajax.core :as ajax]))

;;;;;;;;;;;;;; fetch place ;;;;;;;;;;;

(rf/reg-event-fx
  :place/fetch
  (fn [_ [_]]
    {:http-xhrio {:method          :get
                  :uri             (str "/api/places")
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-failure      [:place/fetch-on-failure]
                  :on-success      [:place/fetch-on-success]}}))

(rf/reg-event-db
  :place/fetch-on-success
  (fn [db [_ resp]] ;data, message
    (if (nil? (:message resp))
      (assoc db :place/fetch-data (:data resp))
      (assoc db :place/fetch-failure resp))))

(rf/reg-event-db
  :place/fetch-on-failure
  (fn [db [_ error]]
    (assoc db :place/fetch-failure
              {:data  nil
               :message (str "获取 places 信息失败，" error)})))

(rf/reg-sub
  :place/fetch-data
  (fn [db _]
    (:place/fetch-data db)))

(rf/reg-sub
  :place/fetch-failure
  (fn [db _]
    (:place/fetch-failure db)))

(rf/reg-event-db
  :place/fetch-clean-failure
  (fn [db _]
    (dissoc db :place/fetch-failure)))

;;;;;;;;;;;;;;;;;; new place ;;;;;;;;;;;;;;
(rf/reg-event-fx
  :place/new
  (fn [_ [_ data]]
    {:http-xhrio {:method          :post
                  :params          data
                  :uri             (str "/api/place")
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-failure      [:place/new-on-failure]
                  :on-success      [:place/new-on-success]}}))


(rf/reg-event-db
  :place/new-on-success
  (fn [db [_ resp]] ;data, message, status
    (assoc db :place/new-failure resp)))

(rf/reg-event-db
  :place/new-on-failure
  (fn [db [_ error]]
    (assoc db :place/new-failure
              {:data  nil
               :status 0
               :message (str "新建 place 信息失败，" error)})))

(rf/reg-sub
  :place/new-failure
  (fn [db _]
    (:place/new-failure db)))

(rf/reg-event-db
  :place/new-clean-failure
  (fn [db _]
    (dissoc db :place/new-failure)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(rf/reg-event-fx
  :package/new
  (fn [_ [_ data]]
    {:http-xhrio {:method          :post
                  :params          data
                  :uri             (str "/api/package")
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-failure      [:package/new-on-failure]
                  :on-success      [:package/new-on-success]}}))


(rf/reg-event-db
  :package/new-on-success
  (fn [db [_ resp]] ;data, message, status
    (assoc db :package/new-failure resp)))

(rf/reg-event-db
  :package/new-on-failure
  (fn [db [_ error]]
    (assoc db :package/new-failure
              {:data  nil
               :status 0
               :message (str "新建 package 信息失败，" error)})))

(rf/reg-sub
  :package/new-failure
  (fn [db _]
    (:package/new-failure db)))

(rf/reg-event-db
  :package/new-clean-failure
  (fn [db _]
    (dissoc db :package/new-failure)))
