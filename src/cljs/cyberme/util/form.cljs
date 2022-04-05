(ns cyberme.util.form
  (:require [re-frame.core :as rf]
            [cyberme.modals :as modals]
            [reagent.core :as r]
            [cyberme.validation :as va]
            [clojure.string :as string]))

(declare dialog)

(dialog :add-movie
        "添加追踪美剧"
        [[:name "名称 *" "美剧名"]
         [:url "追踪地址 *" "http 或 https 开头"]]
        "确定"
        (fn [f e] (println f) {})
        {:subscribe-ajax    [:movie-data]
         :call-when-exit    [[:movie-data-clean]
                             [:common/navigate! :dashboard]]
         :call-when-success [[:movie-data-clean]]})

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
  ; {:type :textarea :attr {:rows 2}}]"
  [id title bodies footer-text validate-submit
   {:keys [subscribe-ajax call-when-exit call-when-success]}]
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
                                 [:<>
                                  (if (vector? k)
                                    [:option {:value (first k)} (second k)]
                                    [:option {:value k} k])])])]
                           (when-let [message @e]
                             [:p.help.is-danger message])]))]
      (let [server-back (if subscribe-ajax @(rf/subscribe subscribe-ajax) nil)]
        (modals/modal-card
          id title
          [:div {:style {:color "black"}}
           (mapv #(into [common-fields] %) bodies)
           (when server-back
             [(if (= (:status server-back) 1)
                :div.notification.is-success.mt-4
                :div.notification.is-danger.mt-4)
              [:blockquote (:message server-back)]])]
          (let [is-success-call (and (not (nil? server-back))
                                     (= (:status server-back) 1))]
            [:button.button.is-primary.is-fullwidth
             {:on-click (if is-success-call
                          (fn [_]
                            (reset! fields {})
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
          #(doseq [event call-when-exit] (rf/dispatch event)))))))