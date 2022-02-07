(ns icemanager.place-edit
  (:require [re-frame.core :as rf]
            [icemanager.modals :as modals]
            [reagent.core :as r]
            [icemanager.request :as req]
            [icemanager.validation :as va]))

(defn place-edit-holder []
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
                    error (va/validate-edit-place raw-data)
                    raw-data (if (nil? (:description raw-data))
                               (assoc raw-data :description nil)
                               raw-data)]
                (if error (reset! errors error)
                          ;一定具备字段 id place location description
                          (rf/dispatch [:place/edit raw-data]))))]
      (let [server-back @(rf/subscribe [:place/edit-callback])
            {:keys [id place location description] :as current-place}
            @(rf/subscribe [:place/current])
            _ (reset! fields current-place)]
        (modals/modal-card
          :edit-place
          (str "修改位置：" place " [id: " id "]")
          [:div {:style {:color "black"}}
           [common-fields :place "位置名称 *" "位置的中文名称，比如 抽屉#1"]
           [common-fields :location "位置地点 *" "位置所在的地理位置，比如 洪山区"]
           [common-fields :description "位置概述" "简短的描述如何找到此位置，比如 衣帽间靠右第一个抽屉"
            {:type :textarea :attr {:rows 2}}]
           [:div.is-pulled-left.is-clickable.dui-tips
            {:data-tooltip "物品将移动到 #1 位置, #1 不可删除"
             :on-click     (fn [_]
                             (reset! fields {})
                             (reset! errors {})
                             (rf/dispatch [:place/edit-callback-clean]) ;清空 ajax 返回的数据
                             (rf/dispatch [:app/hide-modal :edit-place]) ;关闭模态框
                             (rf/dispatch [:place/current-clean]) ;清空当前修改位置的数据
                             (rf/dispatch
                               [:global/notice {:message  "确定删除此位置吗，此操作不可恢复！"
                                                :callback [:place/delete id]}]))}
            (when-not server-back
              [:span.icon-text.has-text-danger
               [:span.icon
                [:i.fa.fa-trash {:style {:margin-top :2px}}]]
               [:span "删除此位置"]])]
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
                            (rf/dispatch [:place/edit-callback-clean]) ;清空 ajax 返回的数据
                            (rf/dispatch [:app/hide-modal :edit-place]) ;关闭模态框
                            (rf/dispatch [:place/current-clean]) ;清空当前修改位置的数据
                            (rf/dispatch [:place/fetch]))   ;重新加载主界面
                          (fn [_] (submit-add)))}
             (if is-success-call "关闭" "提交修改")])
          fields errors (fn [_]                             ;手动关闭，会自动关闭模态框
                          (rf/dispatch [:place/edit-callback-clean]) ;清空 ajax 返回的数据
                          (rf/dispatch [:place/current-clean]))))))) ;清空当前修改位置的数据