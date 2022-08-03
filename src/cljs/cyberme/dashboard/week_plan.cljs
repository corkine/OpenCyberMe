(ns cyberme.dashboard.week-plan
  (:require [cyberme.util.form :refer [dialog]]
            [cyberme.validation :as va]
            [goog.string :as gstring]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn week-plan-add-dialog
  "添加本周计划项目，必须传入 progress-delta 项，可以有 name，description，id，update"
  []
  (dialog :add-week-plan!
          "添加周计划项目"
          [[:name "名称*" "计划名称"]
           [:category "类别*" "当前计划的类别" {:type :select :selects ["learn" "work" "fitness" "diet"]}]
           [:description "详述" "计划详述" {:type :textarea :attr {:rows 3}}]
           [:progress "进度" "当前的计划进度"]
           #_[:id "编号" "当前的计划编号"]]
          "确定"
          #(if-let [err (va/validate! @%1 [[:name va/required] [:category va/required]])]
             (reset! %2 err)
             (rf/dispatch [:dashboard/week-plan-add-item @%1]))
          {:subscribe-ajax    [:dashboard/week-plan-add-item-data]
           :call-when-exit    [[:dashboard/week-plan-add-item-clean]]
           :call-when-success [[:dashboard/week-plan-add-item-clean]
                               [:dashboard/plant-week]]
           :origin-data       {:category "learn" :progress "0.0"}}))

(defn week-plan-log-add-dialog
  "添加本周计划项目日志，需要传入至少 name, category,
  可选 description, progress, id，其中 category 为 learn/work/fitness/diet"
  []
  (dialog :add-week-plan-log!
          "添加项目日志"
          [[:name "名称*" "日志名称"]
           [:progress-delta "进度*" "当前计划项目的进度"]
           [:description "详述" "日志详述" {:type :textarea :attr {:rows 3}}]
           [:update "更新日期" "日志日期"]
           #_[:id "编号" "当前的日志编号"]]
          "确定"
          #(if-let [err (va/validate! @%1 [[:name va/required] [:progress-delta va/number-str]])]
             (reset! %2 err)
             (rf/dispatch [:dashboard/week-plan-item-add-log
                           (merge {:item-id @(rf/subscribe [:week-plan-db-query :item-id])} @%1)]))
          {:subscribe-ajax    [:dashboard/week-plan-item-add-log-data]
           :call-when-exit    [[:dashboard/week-plan-item-add-log-clean]]
           :call-when-success [[:dashboard/week-plan-item-add-log-clean]]
           :origin-data       {:progress-delta "10.0"}}))

(rf/reg-event-db
  :week-plan-db-set
  (fn [db [_ key value]]
    (assoc-in db [:week-plan key] value)))

(rf/reg-sub
  :week-plan-db-query
  (fn [db [_ key]]
    (get (:week-plan db) key)))

(defn plan-widget
  [week-plans]
  [:<>
   (doall
     (for [{:keys [id name logs category progress description]} week-plans]
       ^{:key id}
       [:<>
        (let [show-cate (case category "learn" "学习" "work" "工作" "fitness" "健身" "diet" "饮食" "其他")
              item-id id]
          [:div.mt-0
           [:p.mb-1
            [:span.tag.is-small.is-rounded.is-size-7.mr-2.is-primary.is-light.is-clickable.show-delete
             {:on-click #(rf/dispatch [:global/notice
                                       {:message  (str "是否要删除项目" name "?")
                                        :callback [[:dashboard/week-plan-delete-item id]]}])}
             show-cate]
            [:span.is-clickable
             {:title    (or description id) :style {:vertical-align "-10%"}
              :on-click #(rf/dispatch [:week-plan-db-set item-id
                                       (not @(rf/subscribe [:week-plan-db-query item-id]))])} name]
            [:span.ml-2.is-family-code.is-clickable
             {:style {:vertical-align "-10%"}
              :title (gstring/format "点击新建日志\n完成百分比 %d%%\n包含 %s 日志" progress (count logs))
              :on-click #(do
                           (rf/dispatch [:week-plan-db-set :item-id item-id])
                           (rf/dispatch [:app/show-modal :add-week-plan-log!]))}
             (gstring/format "%d%%" progress)]]
           (when @(rf/subscribe [:week-plan-db-query item-id])
             (for [{:keys [id name description progress-delta update]} logs]
               ^{:key id}
               [:<>
                (let []
                  [:p.is-size-7.ml-1.my-1.is-clickable
                   {:title    (str (if description (str description "\n") "") update)}
                   [:span.tag.is-small.is-size-7.mr-2.is-light.show-delete-2
                    {:on-click #(rf/dispatch
                                  [:global/notice
                                   {:message  (str "是否要删除日志" name "?")
                                    :callback [[:dashboard/week-plan-item-delete-log [item-id id]]]}])} "+" progress-delta]
                   name])]))])]))])