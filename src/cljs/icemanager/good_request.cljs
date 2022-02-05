(ns icemanager.good-request
  (:require [re-frame.core :as rf]
            [ajax.core :as ajax]))

;;;;;;;;;;;;;; delete good ;;;;;;;;;;;

(rf/reg-event-fx
  :good/delete
  (fn [_ [_ id]]
    {:http-xhrio {:method          :post
                  :uri             (str "/api/good/" id "/delete")
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-failure      [:good/delete-on-failure]
                  :on-success      [:good/delete-on-success]}}))

(rf/reg-event-db
  :good/delete-on-success
  (fn [db [_ resp]]                                         ;status message data
    (rf/dispatch [:global/notice
                  {:message (if (= (:status resp) 0)
                              (str "删除物品失败：" (:message resp))
                              (str "删除物品成功：" (:message resp)))
                   :callback (if (not= (:status resp) 0)
                               [:place/fetch]
                               nil)}])
    db))

(rf/reg-event-db
  :good/delete-on-failure
  (fn [db [_ error]]
    (rf/dispatch [:global/notice
                  {:message (str "删除物品失败！" (:last-error error))}])
    db))

;;;;;;;;;;;;;; hide good ;;;;;;;;;;;

(rf/reg-event-fx
  :good/hide
  (fn [_ [_ id]]
    {:http-xhrio {:method          :post
                  :uri             (str "/api/good/" id "/hide")
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-failure      [:good/hide-on-failure]
                  :on-success      [:good/hide-on-success]}}))

(rf/reg-event-db
  :good/hide-on-success
  (fn [db [_ resp]] ;status message data
    (rf/dispatch [:global/notice
                  {:message (if (= (:status resp) 0)
                              (str "删除（隐藏）物品失败：" (:message resp))
                              (str "删除（隐藏）物品成功：" (:message resp)))
                   :callback (if (not= (:status resp) 0)
                               [:place/fetch]
                               nil)}])
    db))

(rf/reg-event-db
  :good/hide-on-failure
  (fn [db [_ error]]
    (rf/dispatch [:global/notice
                  {:message (str "删除（隐藏）物品失败！" error)}])
    db))

;;;;;;;;;;;;;;;;;;; new place ;;;;;;;;;;;;;;
;(rf/reg-event-fx
;  :place/new
;  (fn [_ [_ data]]
;    {:http-xhrio {:method          :post
;                  :params          data
;                  :uri             (str "/api/place")
;                  :format          (ajax/json-request-format)
;                  :response-format (ajax/json-response-format {:keywords? true})
;                  :on-failure      [:place/new-on-failure]
;                  :on-success      [:place/new-on-success]}}))
;
;
;(rf/reg-event-db
;  :place/new-on-success
;  (fn [db [_ resp]] ;data, message, status
;    (assoc db :place/new-failure resp)))
;
;(rf/reg-event-db
;  :place/new-on-failure
;  (fn [db [_ error]]
;    (assoc db :place/new-failure
;              {:data  nil
;               :status 0
;               :message (str "新建 place 信息失败，" error)})))
;
;(rf/reg-sub
;  :place/new-failure
;  (fn [db _]
;    (:place/new-failure db)))
;
;(rf/reg-event-db
;  :place/new-clean-failure
;  (fn [db _]
;    (dissoc db :place/new-failure)))
;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;(rf/reg-event-fx
;  :package/new
;  (fn [_ [_ data]]
;    {:http-xhrio {:method          :post
;                  :params          data
;                  :uri             (str "/api/package")
;                  :format          (ajax/json-request-format)
;                  :response-format (ajax/json-response-format {:keywords? true})
;                  :on-failure      [:package/new-on-failure]
;                  :on-success      [:package/new-on-success]}}))
;
;
;(rf/reg-event-db
;  :package/new-on-success
;  (fn [db [_ resp]] ;data, message, status
;    (assoc db :package/new-failure resp)))
;
;(rf/reg-event-db
;  :package/new-on-failure
;  (fn [db [_ error]]
;    (assoc db :package/new-failure
;              {:data  nil
;               :status 0
;               :message (str "新建 package 信息失败，" error)})))
;
;(rf/reg-sub
;  :package/new-failure
;  (fn [db _]
;    (:package/new-failure db)))
;
;(rf/reg-event-db
;  :package/new-clean-failure
;  (fn [db _]
;    (dissoc db :package/new-failure)))