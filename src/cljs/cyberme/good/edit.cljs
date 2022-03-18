(ns cyberme.good.edit
  (:require [re-frame.core :as rf]
            [cyberme.modals :as modals]
            [reagent.core :as r]
            [clojure.string :as string]))

(def good-status ["活跃","收纳","移除"])

(def default-place-id "1")

(defn validate-map-good-edit
  "一定要提供的字段：id,uid,name,label(默认空列表),status(默认活跃),placeId"
  [{:keys [name uid status note labels placeId]
    :or {status (first good-status)} :as raw}]
  (cond (or (nil? placeId) (string/blank? placeId)) {:error {:name "位置不能为空"}}
        (or (nil? name) (string/blank? name)) {:error {:name "名称不能为空"}}
        (and (not (or (nil? uid) (string/blank? uid)))
             (<= (count uid) 4)) {:error {:uid "编号不能小于 4 个字符（如果提供的话）"}}
        :else (let [tags-list (string/split (string/replace (or labels "") "，" ",") #",")
                    tags-list (filter (comp not string/blank?) tags-list)
                    clean-tags-list (vec (map string/trim tags-list))
                    fix-labels-status-data (assoc raw :labels clean-tags-list :status status)
                    have-uid (not (or (nil? uid) (string/blank? uid)))]
                {:data
                 (if have-uid
                   (assoc fix-labels-status-data :uid (string/upper-case uid))
                   (assoc fix-labels-status-data :uid nil))})))

(def to-modal-top
  #(-> js/document
      (.getElementById "edit_good")
      .scrollIntoView))

(defn edit-good-holder []
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
                             [:p.help.is-danger message])]))
            (submit-edit []
              (let [raw-data @fields
                    raw-data (if (string/blank? (:placeId raw-data))
                               (assoc raw-data :placeId default-place-id) raw-data)
                    {:keys [data error]} (validate-map-good-edit raw-data)
                    ;_ (println "final data: " data)
                    ;_ (reset! fields
                    ;          (assoc data :labels
                    ;                      (if (empty? (:labels data))
                    ;                        nil (string/join "," (:labels data)))))
                    ;_ (println @fields)
                    ]
                (if error (reset! errors error)
                          (rf/dispatch [:good/edit data]))))]
      (let [server-back @(rf/subscribe [:good/edit-callback])
            is-success-call (and (not (nil? server-back)) (= (:status server-back) 1))
            {:keys [name id uid status note labels placeId] :as current-good}
            @(rf/subscribe [:good/current])
            ;_ (println "input data: " current-good)
            {{:keys [places]} :data} @(rf/subscribe [:recent/fetch-data])
            places (sort-by :id places)
            _ (reset! fields {:name name
                              :id id
                              :uid uid
                              :status status
                              :note note
                              :placeId placeId
                              :labels (if (empty? labels)
                                        nil (string/join "," labels))})]
        (modals/modal-card
          :edit-good
          (str "修改物品：" name " [id:" id "]")
          [:div.pt-3 {:style {:color "black"}
                 :id "edit_good"}
           (when-not is-success-call
             [:<>
              [common-fields :name "名称 *" "物品名称"]
              [common-fields :status "状态 *" "物品所处的状态"
               {:type :select :selects good-status}]
              [common-fields :placeId "位置 *" "物品所处的位置"
               {:type :select :selects (map #(vector (:id %) (:place %)) places)}]
              [common-fields :uid "编号" "个人物品编码 ID，以 CM 开头，比如 CMPRO"]
              [common-fields :labels "标签" "可输入零个或多个，使用逗号分开"]
              [common-fields :note "概述" "简短的描述物品特征，比如保质期、存放条件等"
               {:type :textarea :attr {:rows 2}}]
              [:div.is-pulled-left.is-clickable.dui-tips
               {:data-tooltip "删除(保留数据)"
                :on-click     (fn [_]
                                (reset! fields {})
                                (reset! errors {})
                                (rf/dispatch [:good/edit-callback-clean]) ;清空 ajax 返回的数据
                                (rf/dispatch [:app/hide-modal :edit-good]) ;关闭模态框
                                (rf/dispatch [:good/current-clean]) ;清空当前修改物品的数据
                                (rf/dispatch
                                  [:global/notice {:message  "确定删除此物品吗？"
                                                   :callback [:good/hide id]}]))}
               (when-not server-back
                 [:span.icon-text.has-text-danger
                  [:span.icon
                   [:i.fa.fa-ban {:style {:margin-top :2px}}]]
                  [:span.mr-2 "隐藏"]])]
              [:div.is-pulled-left.is-clickable.dui-tips
               {:data-tooltip "删除(不可恢复)"
                :on-click     (fn [_]
                                (reset! fields {})
                                (reset! errors {})
                                (rf/dispatch [:good/edit-callback-clean]) ;清空 ajax 返回的数据
                                (rf/dispatch [:app/hide-modal :edit-good]) ;关闭模态框
                                (rf/dispatch [:good/current-clean]) ;清空当前修改物品的数据
                                (rf/dispatch
                                  [:global/notice {:message  "确定删除此物品吗，此操作不可恢复！"
                                                   :callback [:good/delete id]}]))}
               (when-not server-back
                 [:span.icon-text.has-text-danger
                  [:span.icon
                   [:i.fa.fa-trash {:style {:margin-top :2px}}]]
                  [:span "删除"]])]])
           (when server-back
             [(if (= (:status server-back) 1)
                :div.notification.is-success.mt-4
                :div.notification.is-danger.mt-4)
              [:blockquote (:message server-back)]])]
          [:button.button.is-primary.is-fullwidth
           {:on-click (if is-success-call
                        (fn [_]
                          (reset! fields {})
                          (reset! errors {})
                          (rf/dispatch [:good/edit-callback-clean])
                          (rf/dispatch [:app/hide-modal :edit-good])
                          (rf/dispatch [:good/current-clean])
                          (rf/dispatch [:place/fetch])
                          (to-modal-top))
                        (fn [_]
                          (reset! errors {})
                          (submit-edit)))}
           (if is-success-call "关闭" "修改！")]
          fields errors (fn [_]
                          (rf/dispatch [:good/edit-callback-clean])
                          (rf/dispatch [:good/current-clean])
                          (to-modal-top)))))))