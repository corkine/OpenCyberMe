(ns icemanager.modals
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [reitit.frontend.easy :as rfe]
    [reitit.frontend.controllers :as rfc]))

(rf/reg-event-db
  :app/show-modal
  (fn [db [_ modal-id]]
    (assoc-in db [:app/active-modals modal-id] true)))

(rf/reg-event-db
  :app/hide-modal
  (fn [db [_ modal-id]]
    (update db :app/active-modals dissoc modal-id)))

(rf/reg-sub
  :app/active-modals
  (fn [db _] (:app/active-modals db {})))

(rf/reg-sub
  :app/modal-showing?
  :<- [:app/active-modals]
  (fn [modals [_ modal-id]]
    (get modals modal-id false)))

(defn modal-card
  ([id title body footer]
   [:div.modal {:class (when @(rf/subscribe [:app/modal-showing? id])
                         "is-active")}
    [:div.modal-background {:on-click #(rf/dispatch [:app/hide-modal id])}]
    [:div.modal-card [:header.modal-card-head
                      [:p.modal-card-title {:style {:margin-bottom :0px}} title]
                      [:button.delete
                       {:on-click #(rf/dispatch [:app/hide-modal id])}]]
     [:section.modal-card-body body]
     [:footer.modal-card-foot footer]]])
  ([id title body footer fields errors close-fn]
   [:div.modal {:class (when @(rf/subscribe [:app/modal-showing? id])
                         "is-active")}
    [:div.modal-background {:on-click (fn [_] (reset! fields {})
                                        (reset! errors {})
                                        (when-not (nil? close-fn) (close-fn))
                                        (rf/dispatch [:app/hide-modal id]))}]
    [:div.modal-card [:header.modal-card-head
                      [:p.modal-card-title {:style {:margin-bottom :0px}} title]
                      [:button.delete
                       {:on-click (fn [_]
                                    (reset! fields {})
                                    (reset! errors {})
                                    (when-not (nil? close-fn) (close-fn))
                                    (rf/dispatch [:app/hide-modal id]))}]]
     [:section.modal-card-body body]
     [:footer.modal-card-foot footer]]])
  ([id title body footer close-fn]
   [:div.modal {:class (when @(rf/subscribe [:app/modal-showing? id])
                         "is-active")}
    [:div.modal-background {:on-click (fn [_]
                                        (when-not (nil? close-fn) (close-fn))
                                        (rf/dispatch [:app/hide-modal id]))}]
    [:div.modal-card [:header.modal-card-head
                      [:p.modal-card-title {:style {:margin-bottom :0px}} title]
                      [:button.delete
                       {:on-click (fn [_]
                                    (when-not (nil? close-fn) (close-fn))
                                    (rf/dispatch [:app/hide-modal id]))}]]
     [:section.modal-card-body body]
     [:footer.modal-card-foot footer]]]))

(defn modal-button
  ([id title body footer]
   [modal-button id {:button {:class ["is-primary"]}} title body footer])
  ([id opts title body footer]
   [:div [:button.button
          (merge (:button opts)
                 {:on-click #(rf/dispatch [:app/show-modal id])})
          title]
    [modal-card id title body footer]])
  ([id opts icon title body footer]
   [:div [:button.button
          (merge (:button opts)
                 {:on-click #(rf/dispatch [:app/show-modal id])})
          [:span.icon-text
           [:span.icon icon]
           [:span title]]]
    [modal-card id title body footer]])
  ([id opts icon title body footer fields errors close-fn]
   [:div [:button.button
          (merge (:button opts)
                 {:on-click #(rf/dispatch [:app/show-modal id])})
          [:span.icon-text
           [:span.icon icon]
           [:span title]]]
    [modal-card id title body footer fields errors close-fn]]))