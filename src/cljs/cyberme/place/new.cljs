(ns cyberme.place.new
  (:require [re-frame.core :as rf]
            [cyberme.modals :as modals]
            [reagent.core :as r]
            [cyberme.validation :as va]))

(defn new-place-btn []
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
                    error (va/validate-add-place raw-data)
                    raw-data (if (nil? (:description raw-data))
                               (assoc raw-data :description nil)
                               raw-data)]
                (if error (reset! errors error)
                          (rf/dispatch [:place/new raw-data]))))]
      (let [server-back @(rf/subscribe [:place/new-failure])]
        #_(modals/modal-card
            :create-new-place
            "新位置"
            [:div {:style {:color "black"}}
             #_[common-fields :id "位置编号 *" "大写且唯一，仅能包含 - _ 和英文字母"]
             [common-fields :place "位置名称 *" "位置的中文名称，比如 抽屉#1"]
             [common-fields :location "位置地点 *" "位置所在的地理位置，比如 洪山区"]
             [common-fields :description "位置概述" "简短的描述如何找到此位置，比如 衣帽间靠右第一个抽屉"
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
                              (rf/dispatch [:place/new-clean-failure])
                              (rf/dispatch [:app/hide-modal :create-new-place])
                              (rf/dispatch [:recent/fetch])
                              (rf/dispatch [:place/fetch]))
                            (fn [_] (submit-add)))}
               (if is-success-call "关闭" "创建")])
            fields errors #(rf/dispatch [:place/new-clean-failure]))
        (modals/modal-button
          :create-new-place
          {:button {:class [:is-info]}}
          [:i.fa.fa-inbox {:aria-hidden "true"}]
          "新位置"
          [:div {:style {:color "black"}}
           #_[common-fields :id "位置编号 *" "大写且唯一，仅能包含 - _ 和英文字母"]
           [common-fields :place "位置名称 *" "位置的中文名称，比如 抽屉#1"]
           [common-fields :location "位置地点 *" "位置所在的地理位置，比如 洪山区"]
           [common-fields :description "位置概述" "简短的描述如何找到此位置，比如 衣帽间靠右第一个抽屉"
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
                            (rf/dispatch [:place/new-clean-failure])
                            (rf/dispatch [:app/hide-modal :create-new-place])
                            (rf/dispatch [:recent/fetch])
                            (rf/dispatch [:place/fetch]))
                          (fn [_] (submit-add)))}
             (if is-success-call "关闭" "创建")])
          fields errors #(rf/dispatch [:place/new-clean-failure]))))))