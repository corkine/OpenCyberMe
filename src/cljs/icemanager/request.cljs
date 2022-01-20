(ns icemanager.request
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [reitit.frontend.easy :as rfe]
    [reitit.frontend.controllers :as rfc]))

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

(rf/reg-event-fx
  :fetch-feature
  (fn [_ [_ rs-id-lower]]
    {:http-xhrio {:method :get
                  :uri (str "/api/feature/" rs-id-lower)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:set-feature]}}))

(rf/reg-event-fx
  :update-feature
  (fn [_ [_ id data]]
    {:http-xhrio {:method :post
                  :params data
                  :uri (str "/api/feature/" id)
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-failure [:set-update-feature-error]
                  :on-success [:set-update-feature-success]}}))

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
