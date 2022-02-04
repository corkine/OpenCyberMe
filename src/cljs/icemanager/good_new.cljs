(ns icemanager.good-new
  (:require [re-frame.core :as rf]
            [icemanager.modals :as modals]
            [reagent.core :as r]
            [icemanager.validation :as va]
            [clojure.string :as string]))

(def good-status ["活跃","收纳","移除"])

(defn validate-map-good-add [{:keys [name uid status note labels]
                              :or {status (first good-status)} :as raw}]
  (cond (or (nil? name) (string/blank? name)) {:error {:name "名称不能为空"}}
        (and (not (or (nil? uid) (string/blank? uid)))
             (<= (count uid) 4)) {:error {:uid "编号不能小于 4 个字符（如果提供的话）"}}
        :else (let [tags-list (string/split (string/replace (or labels "") "，" ",") #",")
                    clean-tags-list (vec (map string/trim tags-list))
                    fix-labels-status-data (assoc raw :labels clean-tags-list :status status)
                    have-uid (not (or (nil? uid) (string/blank? uid)))]
                {:data
                 (if have-uid
                   (assoc fix-labels-status-data :uid (string/upper-case uid))
                   (dissoc fix-labels-status-data :uid))})))

(defn new-good-btn []
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
                    {:keys [data error]} (validate-map-good-add raw-data)
                    _ (println data)
                    _ (println "error:" error)]
                (if error (reset! errors error)
                          (rf/dispatch [:add-good raw-data]))))]
      (let [server-back @(rf/subscribe [:add-good-server-back])]
        (modals/modal-button
          :create-new-good
          {:button {:class [:is-primary]}}
          [:i.fa.fa-plus-square {:aria-hidden "true"}]
          "物品入库"
          [:div {:style {:color "black"}}
           [common-fields :name "名称 *" "物品名称"]
           [common-fields :status "状态 *" "物品所处的状态"
            {:type :select :selects good-status}]
           [common-fields :uid "编号" "个人物品编码 ID，以 CM 开头，比如 CMPRO"]
           [common-fields :labels "标签" "可输入零个或多个，使用逗号分开"]
           [common-fields :note "概述" "简短的描述物品特征，比如保质期、存放条件等"
            {:type :textarea :attr {:rows 2}}]
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
                            (rf/dispatch [:clean-add-good-server-back])
                            (rf/dispatch [:app/hide-modal :create-new-good]))
                          (fn [_] (submit-add)))}
             (if is-success-call "关闭" "入库！")])
          fields errors)))))
;modal：create-new-good
;ajax：add-good, add-good-server-back, clean-add-good-server-back