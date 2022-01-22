(ns icemanager.feature-new
  (:require [re-frame.core :as rf]
            [icemanager.modals :as modals]
            [reagent.core :as r]
            [clojure.string :as string]
            [icemanager.feature :as feature]))

(defn feature-add-check [{:keys [rs_id title
                                 ] :as origin}]
  (let [rs_check (not (string/blank? rs_id))
        new_rs (string/upper-case (or rs_id ""))
        title_check (not (string/blank? title))]
    {:error (if (and rs_check title_check)
              nil
              {:rs_id (if (not rs_check) "RS 编号不合法！" nil)
               :title (if (not title_check) "特性名称不能为空" nil)})
     :data  (merge origin {:rs_id new_rs})}))

(defn new-feature-btn []
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
            (submit-feature-add []
              (let [raw-data @fields
                    _ (if-not (:version raw-data) (swap! fields assoc :version (first feature/ice-versions)))
                    _ (if-not (:status raw-data) (swap! fields assoc :status (first feature/ice-status)))
                    {:keys [error data]} (feature-add-check @fields)]
                (if error (reset! errors error)
                          (rf/dispatch [:add-feature data]))))]
      (let [server-back @(rf/subscribe [:add-feature-server-back])]
        (modals/modal-button
          :create-new-feature
          "新建特性"
          [:div {:style {:color "black"}}
           [common-fields :rs_id "RS 编号 *" "特性 ID 号，大写且唯一，仅能包含 - _ 和英文字母"]
           [common-fields :title "特性名称 *" "特性的中文名称"]
           [common-fields :description "特性概述" "简短的描述特性用途，可为空"
            {:type :textarea :attr {:rows 2}}]
           [common-fields :version "所属版本 *" "特性引入的版本"
            {:type :select :selects feature/ice-versions}]
           [common-fields :status "当前状态 *" "当前特性的开发状态，可为空"
            {:type :select :selects feature/ice-status}]
           (when server-back
             [(if (= (:status server-back) :success)
                :div.notification.is-success.mt-4
                :div.notification.is-danger.mt-4)
              [:blockquote (:content server-back)]])]
          (let [is-success-call (and (not (nil? server-back))
                                     (= (:status server-back) :success))]
            [:button.button.is-primary.is-fullwidth
             {:on-click (if is-success-call
                          (fn [_]
                            (reset! fields {})
                            (reset! errors {})
                            (rf/dispatch [:clean-add-feature-server-back])
                            (rf/dispatch [:fetch-features])
                            (rf/dispatch [:app/hide-modal :create-new-feature]))
                          (fn [_] (submit-feature-add)))}
             (if is-success-call "关闭" "执行")]))))))