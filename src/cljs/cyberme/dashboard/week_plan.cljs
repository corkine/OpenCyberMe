(ns cyberme.dashboard.week-plan
  (:require [cyberme.util.form :refer [dialog]]
            [cyberme.validation :as va]
            [goog.string :as gstring]
            [re-frame.core :as rf]
            [cyberme.util.tool :as tool]
            [clojure.string :as str]))

(defn week-plan-modify-item-dialog
  "特定计划项目更新对话框"
  [success-update]
  (let [item @(rf/subscribe [:week-plan-db-query :modify-item])]
    (dialog :modify-week-plan-item!
            "更新周计划项目"
            [[:name "名称*" "计划名称"]
             [:description "详述" "计划详述"
              {:type :textarea :attr
               {:rows (max 3
                           (count (str/split (or (-> item :description) "") "\n")))}}]]
            "确定"
            #(if-let [err (va/validate! @%1 [[:name va/required]])]
               (reset! %2 err)
               (rf/dispatch [:dashboard/week-plan-modify-item (assoc @%1 :date (:date item))]))
            {:subscribe-ajax            [:dashboard/week-plan-modify-item-data]
             :call-when-exit            [[:dashboard/week-plan-modify-item-clean]
                                         [:week-plan-db-unset :modify-item]]
             :call-when-success         [[:dashboard/week-plan-modify-item-clean]
                                         [success-update]]
             :origin-data               (select-keys item [:name :description :id])
             :origin-data-is-subscribed true})))

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
                               #_[:dashboard/plant-week]]
           :origin-data       {:category "learn" :progress "0.0"}}))

(defn week-plan-log-add-dialog
  "添加本周计划项目日志，需要传入至少 name, category,
  可选 description, progress, id，其中 category 为 learn/work/fitness/diet"
  []
  (let [may-next-log-todo-item @(rf/subscribe [:week-plan/may-next-finish-item-log])
        may-next-log-todo-item-title (:title may-next-log-todo-item)
        may-next-log-todo-item-date (:time may-next-log-todo-item)]
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
                             (merge {:item-id (-> @(rf/subscribe [:week-plan-db-query :current-item])
                                                  :id)} @%1)]))
            {:subscribe-ajax            [:dashboard/week-plan-item-add-log-data]
             :call-when-exit            [[:dashboard/week-plan-item-add-log-clean]]
             :call-when-success         [[:dashboard/week-plan-item-add-log-clean]]
             :origin-data               (if may-next-log-todo-item
                                          {:progress-delta "10.0"
                                           :name           (str (tool/week-?) "：" may-next-log-todo-item-title)
                                           :description    (str "TODO 项目完成于 " may-next-log-todo-item-date)}
                                          {:progress-delta "10.0"})
             :origin-data-is-subscribed true})))

(defn plan-widget
  [week-plans {:keys [go-diary-add-log show-todo]
               :or   {go-diary-add-log false show-todo false}}]
  [:div.columns
   [(if show-todo :div.column.is-6 :div.column.is-12)
    (doall
      (for [{:keys [id name logs category progress description] :as item} week-plans]
        ^{:key id}
        [:<>
         (let [show-cate (case category "learn" "学习" "work" "工作" "fitness" "健身" "diet" "饮食" "其他")
               item-id id]
           [:div.mt-0.mb-1
            [:p.mb-1
             [:span.tag.is-small.is-rounded.is-size-7.mr-2.is-primary.is-light.is-clickable.show-delete
              {:on-click #(rf/dispatch [:global/notice
                                        {:message  (str "是否要删除项目" name "?")
                                         :callback [[:dashboard/week-plan-delete-item id]]}])}
              show-cate]
             [:span.is-clickable
              {:title    (or description id) :style {:vertical-align "-10%"}
               ;点击标题后，创建 :week-plan :item-id true 项目，表示当前列表已打开
               ;再次点击后，修改为 :week-plan :item-id false，表示当前列表已关闭
               :on-click #(rf/dispatch [:week-plan-db-set item-id
                                        (not @(rf/subscribe [:week-plan-db-query item-id]))])} name]
             [:span.ml-2.is-family-code.is-clickable
              {:style    {:vertical-align "-10%"}
               :title    (gstring/format "点击新建日志\n完成百分比 %d%%\n包含 %s 日志" progress (count logs))
               :on-click #(do
                            (when go-diary-add-log          ;如果今天有日记，则在日记编辑页面弹窗，反之新建日记弹窗
                              (if @(rf/subscribe [:week-plan/today-diary-exist?])
                                (rf/dispatch [:common/navigate! :diary-edit-by-date {:date (tool/today-str)}])
                                (rf/dispatch [:common/navigate! :diary-new])))
                            ;:week-plan :item-id 表示当前选中的项目 id
                            (rf/dispatch [:week-plan-db-set :current-item item])
                            ;(rf/dispatch [:week-plan-db-set :item-id item-id]) ;显示当前展开的 WEEK PLAN ITEM
                            (rf/dispatch [:app/show-modal :add-week-plan-log!]) ;显示对话框
                            )}
              (gstring/format "%d%%" progress)]
             [:span.is-clickable
              {:title    "点击修改此项目"
               :on-click (fn [_]
                           (rf/dispatch [:week-plan-db-set :modify-item item])
                           (rf/dispatch [:app/show-modal :modify-week-plan-item!]))}
              "_"]]
            ;每个 WEEK PLAN ITEM 的 LOG
            (when @(rf/subscribe [:week-plan-db-query item-id])
              [:div.mb-2
               (for [{:keys [id name description progress-delta update]} logs]
                 ^{:key id}
                 [:<>
                  (let []
                    [:p.is-size-7.ml-1.my-1.is-clickable
                     {:title (str (if description (str description "\n") "") update)}
                     [:span.tag.is-small.is-size-7.mr-2.is-light.show-delete-2
                      {:on-click #(rf/dispatch
                                    [:global/notice
                                     {:message  (str "是否要删除日志" name "?")
                                      :callback [[:dashboard/week-plan-item-delete-log [item-id id]]]}])}
                      (gstring/format "+%s" (if (>= progress-delta 10) progress-delta (str "\u00a0\u00a0" progress-delta)))]
                     name])])])])]))]
   [:div.column.is-6.is-size-7
    (when show-todo
      (if @(rf/subscribe [:week-plan-db-query :todo])
        [:div.is-clickable
         {:on-click #(rf/dispatch [:week-plan-db-set :todo nil])
          :style    {:margin-top "-0.5em"}}
         (let [todo @(rf/subscribe [:dashboard/recent-data])
               items (-> todo :data :todo)]
           (for [[day day-items] (vec items)]
             ^{:key day}
             [:<>
              [:p.mb-1.mt-2.is-family-code.has-text-weight-bold day]
              (for [{:keys [list title status time create_at finish_at importance]} day-items]
                ^{:key (str title create_at)}
                [:p.my-1 [:span list] " / " [:span title]])]))]
        [:div.is-clickable.is-size-6
         {:on-click #(rf/dispatch [:week-plan-db-set :todo true])
          :title    (str "点击显示最近待办事项")
          :style    {:text-align   "right"
                     :margin-right "1.3em"}} [:i.fa.fa-info]]))]])