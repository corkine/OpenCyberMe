(ns cyberme.util.form
  (:require [re-frame.core :as rf]
            [cyberme.modals :as modals]
            [reagent.core :as r]
            [cyberme.validation :as va]
            [clojure.string :as string]))

(defn dialog
  ;validate-submit (fields, errors) -> data map
  ;bodies:
  ;[:name "名称 *" "物品名称"]
  ;[:status "状态 *" "物品所处的状态"
  ; {:type :select :selects good-status}]
  ;[:placeId "位置 *" "物品所处的位置"
  ; {:type :select :selects (map #(vector (:id %) (:place %)) places)}]
  ;[:uid "编号" "个人物品编码 ID，以 CM 开头，比如 CMPRO"]
  ;[:labels "标签" "可输入零个或多个，使用逗号分开"]
  ;[:note "概述" "简短的描述物品特征，比如保质期、存放条件等"
  ; {:type :textarea :attr {:rows 2}}]
  ;
  ; origin-data 不支持外部变更后传入
  ; origin-data-is-subscribed 原始数据是否可变，如果可变则损失重绘 dialog 的中间数据保证此数据能在变化时更新"
  [id title bodies footer-text validate-submit
   {:keys [subscribe-ajax call-when-exit call-when-success origin-data origin-data-is-subscribed]}]
  (r/with-let
    [fields (r/atom (or origin-data {}))
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
                                  :else :input.input) ;type
                            (merge (if (= type :select)
                                     {:id id}
                                     {:id        id
                                      :type      (if type type :text)
                                      :value     (str @v)
                                      :on-change #(reset! v (.. % -target -value))})
                                   (if attr attr {})) ;attr
                            (when-not (nil? selects)
                              [:select
                               {:id        id
                                :value     (or @v "")
                                :on-change #(reset! v (.. % -target -value))}
                               (for [k selects]
                                 ^{:key k}
                                 [:<>
                                  (if (vector? k)
                                    [:option {:value (first k)} (second k)]
                                    [:option {:value k} k])])])]
                           (when-let [message @e]
                             [:p.help.is-danger message])]))]
      (let [server-back (if subscribe-ajax @(rf/subscribe subscribe-ajax) nil)
            _ (if (and origin-data origin-data-is-subscribed) (reset! fields origin-data))]
        (modals/modal-card
          id title
          (conj
            (into [:div {:style {:color "black"}}]
                  (mapv #(into [common-fields] %) bodies))
            (when server-back
              [(if (= (:status server-back) 1)
                 :div.notification.is-success.mt-4
                 :div.notification.is-danger.mt-4)
               [:blockquote (:message server-back)]]))
          (let [is-success-call (and (not (nil? server-back))
                                     (= (:status server-back) 1))]
            [:button.button.is-primary.is-fullwidth
             {:on-click (if is-success-call
                          (fn [_]
                            (reset! fields (or origin-data {}))
                            (reset! errors {})
                            (rf/dispatch [:app/hide-modal id])
                            (doseq [event call-when-success]
                              (rf/dispatch event)))
                          (fn [_]
                            (reset! errors {})
                            (validate-submit fields errors)))}
             (if is-success-call "关闭" footer-text)])
          fields
          errors
          (fn []
            (reset! fields (or origin-data {}))
            (doseq [event call-when-exit]
              (rf/dispatch event))))))))