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
                  :uri             "/api/feature/all"
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