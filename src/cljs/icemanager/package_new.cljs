(ns icemanager.package-new
  (:require [re-frame.core :as rf]
            [icemanager.modals :as modals]
            [reagent.core :as r]
            [icemanager.validation :as va]))

(defn new-package-btn []
  (r/with-let
    [fields (r/atom {})
     errors (r/atom {})]
    (letfn [(common-fields [id label hint {:keys [type attr selects]}]
              (r/with-let [v (r/cursor fields [id])
                           e (r/cursor errors [id])]
                          [:div.field
                           [:label.label {:for id} label " "
                            [:span.has-text-grey-light.is-size-7.has-text-weight-light
                             (str hint)]]
                           [(cond (= type :textarea) :textarea.textarea
                                  (= type :select) :div.select
                                  :else :input.input)
                            (merge (if (= type :select)
                                     {:id id}
                                     {:id        id
                                      :type      (if type type :text)
                                      :value     @v
                                      :on-change #(reset! v (.. % -target -value))})
                                   (if attr attr {}))
                            (when-not (nil? selects)
                              [:select
                               {:id        id
                                :value     (or @v "")
                                :on-change #(reset! v (.. % -target -value))}
                               (for [k selects]
                                 ^{:key k}
                                 [:option {:value k} k])])]
                           (when-let [message @e]
                             [:p.help.is-danger message])]))
            (submit-add []
              (let [raw-data @fields
                    error (va/validate-add-package raw-data)]
                (if error (reset! errors error)
                          (rf/dispatch [:package/new raw-data]))))]
      (let [server-back @(rf/subscribe [:package/new-failure])]
        (modals/modal-button
          :create-new-package
          {:button {:class [:is-info :is-inverted :is-outlined]}}
          [:i.fa.fa-inbox {:aria-hidden "true"}]
          "新打包"
          [:div {:style {:color "black"}}
           [common-fields :name "打包名称 *" "尽量简短，言简意赅"]
           [common-fields :description "打包概述" "简短的描述此次打包的用途和设备容量等信息"
            {:type :textarea :attr {:rows 2}}]
           (when server-back
             [(if (not= (:status server-back) 0)
                :div.notification.is-success.mt-4
                :div.notification.is-danger.mt-4)
              [:blockquote (:message server-back)]])]
          (let [is-success-call (and (not (nil? server-back))
                                     (not= (:status server-back) 0))]
            [:button.button.is-primary.is-fullwidth
             {:on-click (if is-success-call
                          (fn [_]
                            (reset! fields {})
                            (reset! errors {})
                            (rf/dispatch [:package/new-clean-failure])
                            (rf/dispatch [:app/hide-modal :create-new-package])
                            (rf/dispatch [:package/fetch]))
                          (fn [_] (submit-add)))}
             (if is-success-call "关闭" "新键打包")])
          fields errors #(rf/dispatch [:package/new-clean-failure]))))))