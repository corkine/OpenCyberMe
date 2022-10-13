(ns cyberme.goal
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [clojure.string :as string]
    [cyberme.validation :as va]
    [clojure.set :as set]
    [cljs-time.core :as t]
    [cyberme.util.request :refer [ajax-flow] :as req]
    [cljs-time.format :as format]
    [reagent.core :as r]
    [cyberme.util.tool :as tool]
    [cyberme.util.form :refer [dialog]]
    [cyberme.dashboard.week-plan :as wp]
    [clojure.string :as str]))

(defonce goal-id (atom 0))

(defonce editing-goal (atom nil))

(def GOAL-TYPE ["money" "fitness" "diet"])

(ajax-flow {:call           :goal/goals
            :uri-fn         #(str "/cyber/goal/goals")
            :data           :goal/goals-data
            :clean          :goal/goals-clean
            :failure-notice true})

(ajax-flow {:call           :goal/goals-edit
            :uri-fn         #(str "/cyber/goal/goals")
            :is-post        true
            :data           :goal/goals-edit-data
            :clean          :goal/goals-edit-clean
            :failure-notice true})

(ajax-flow {:call                   :goal/goals-delete
            :uri-fn                 #(str "/cyber/goal/goals")
            :is-post                true
            :data                   :goal/goals-delete-data
            :clean                  :goal/goals-delete-clean
            :success-callback-event [[:goal/goals-edit-clean]
                                     [:goal/goals]]
            :failure-notice         true})

(rf/reg-event-db
  :goal/ensure-recent-goals!
  (fn [db _]
    (if-let [memory-data (:goal/goals-data db)]
      db
      (do (rf/dispatch [:goal/goals]) db))))

(rf/reg-sub
  :goal/goals-brief
  (fn [db _]
    (if-let [data (:data (:goal/goals-data db))]
      data
      [])))

(defn delete-goal [id]
  (rf/dispatch [:goal/goals-delete {:delete? true :id id}]))

(ajax-flow {:call           :goal/logs-edit
            :uri-fn         #(str "/cyber/goal/goals/" (:goal-id %) "/logs")
            :is-post        true
            :data           :goal/logs-edit-data
            :clean          :goal/logs-edit-clean
            :failure-notice true})

(ajax-flow {:call                   :goal/logs-delete
            :uri-fn                 #(str "/cyber/goal/goals/" (:goal-id %) "/logs")
            :is-post                true
            :data                   :goal/logs-delete-data
            :clean                  :goal/logs-delete-clean
            :success-callback-event [[:goal/logs-delete-clean]
                                     [:goal/goals]]
            :failure-notice         true})

(defn delete-log [goal-id id]
  (rf/dispatch [:goal/logs-delete
                {:delete? true :goal-id goal-id :id id}]))

(defn add-goal
  "添加目标"
  []
  (dialog :add-goal-goal!
          "添加目标"
          [[:name "名称*" "目标名称"]
           [:category "类别*" "目标类别" {:type :select :selects GOAL-TYPE}]
           [:description "详述" "计划详述" {:type :textarea :attr {:rows 2}}]
           [:progress "进度" "当前的计划进度"]]
          "确定"
          #(if-let [err (va/validate! @%1 [[:name va/required] [:category va/required]])]
             (reset! %2 err)
             (let [{:keys [name category description progress]} @%1]
               (rf/dispatch [:goal/goals-edit
                             {:name name
                              :info {:category    category
                                     :description description
                                     :progress    progress}}])))
          {:subscribe-ajax    [:goal/goals-edit-data]
           :call-when-exit    [[:goal/goals-edit-clean]]
           :call-when-success [[:goal/goals-edit-clean]
                               [:goal/goals]]
           :origin-data       {:category (first GOAL-TYPE) :progress "0.0"}}))

(defn edit-goal
  "修改目标"
  []
  (dialog :edit-goal-goal!
          "修改目标"
          [[:name "名称*" "目标名称"]
           [:category "类别*" "目标类别" {:type :select :selects GOAL-TYPE}]
           [:description "详述" "计划详述" {:type :textarea :attr {:rows 2}}]
           [:progress "进度" "当前的计划进度"]]
          "确定"
          #(if-let [err (va/validate! @%1 [[:name va/required] [:category va/required]])]
             (reset! %2 err)
             (let [{:keys [name category description progress id]} @%1]
               (reset! editing-goal
                       {:id   id :name name
                        :info {:category    category
                               :description description
                               :progress    progress}})
               (rf/dispatch [:goal/goals-edit
                             {:name name
                              :id   id
                              :info {:category    category
                                     :description description
                                     :progress    progress}}])))
          {:subscribe-ajax            [:goal/goals-edit-data]
           :call-when-exit            [[:goal/goals-edit-clean]]
           :call-when-success         [[:goal/goals-edit-clean]
                                       [:goal/goals]]
           :origin-data-is-subscribed true
           :origin-data               (if-let [{:keys [id name info]} @editing-goal]
                                        {:id          id :name name
                                         :category    (or (:category info) (first GOAL-TYPE))
                                         :description (:description info)
                                         :progress    (:progress info)}
                                        {:category (first GOAL-TYPE)
                                         :progress "0.0"})}))

(defn add-goal-log
  "添加目标日志"
  []
  (dialog :add-goal-goal-log!
          "添加目标日志"
          [[:name "名称*" "日志名称"]
           [:description "详述" "计划详述" {:type :textarea :attr {:rows 2}}]
           [:earn "进度" "当前增加的目标进度"]]
          "确定"
          #(if-let [err (va/validate! @%1 [[:name va/required] [:earn va/required]])]
             (reset! %2 err)
             (let [{:keys [name description earn]} @%1]
               (rf/dispatch [:goal/logs-edit
                             {:goal-id     @goal-id
                              :name        name
                              :earn        (try
                                             (js/parseFloat (or earn "0"))
                                             (catch js/Error _ 0.0))
                              :description description}])))
          {:subscribe-ajax    [:goal/logs-edit-data]
           :call-when-exit    [[:goal/logs-edit-clean]]
           :call-when-success [[:goal/logs-edit-clean]
                               [:goal/goals]]
           :origin-data       {:earn "0"}}))

(defn goal-main []
  [:div.mt-5.ml-4.mr-4
   [:div
    [:span.title "所有目标"]
    [:span.ml-3.is-clickable.is-size-7
     {:on-click
      #(rf/dispatch [:app/show-modal :add-goal-goal!])} "[新建]"]]
   [add-goal] [add-goal-log] [edit-goal]
   (let [{data :data} @(rf/subscribe [:goal/goals-data])]
     (for [{:keys [id name info create_at update_at logs] :as goal} data] ;;所有 Goals
       ^{:key id}
       [:div.mt-5
        [:p.is-family-code.mb-1.is-clickable
         {:title (str "创建日期：" create_at "\n" "更新日期：" update_at)}
         [:span.tag.is-small.is-size-7.mr-2.is-light.is-clickable.is-rounded
          {:style {:margin-right "5px"
                   :vertical-align "20%"
                   :font-variant "all-petite-caps"}
           :class (case (:category info)
                    "learn" "is-info"
                    "money" "is-info"
                    "diet" "is-success"
                    "fitness" "is-danger"
                    "is-light")}
          (or (:category info) "GOAL")]
         [:span.is-size-5  name]
         [:span.ml-1.is-size-7
          {:on-click #(do (reset! editing-goal goal)
                          (rf/dispatch [:app/show-modal :edit-goal-goal!]))}
          " [修改]"]
         [:span.is-size-7
          {:on-click #(do (reset! goal-id id)
                          (rf/dispatch [:app/show-modal :add-goal-goal-log!]))}
          " [+日志]"]
         [:span.has-text-danger.is-size-7
          {:on-click
           (fn [_]
             (rf/dispatch [:global/notice
                           {:message     (str "确定要删除" name "吗？")
                            :callback-fn #(delete-goal id)}]))}
          " [删除]"]]
        (if-let [desp (:description info)]
          [:p.is-size-7.is-family-code.is-clickable.has-text-grey.ml-1 desp])
        (when (not-empty logs)
          [:pre.mt-2 {:style {:padding "10px 0 10px 9px" :border-radius "0"}}
           (for [{:keys [id goal_id info create_at] :as log} logs] ;;Goal 的每个 Log
             ^{:key id}
             [:<>
              (let [{:keys [name description earn]} info]
                [:<>
                 [:div
                  [:div.tag.is-small.is-size-7.mr-2.is-info.is-light.is-clickable.show-delete-2
                   {:style    {:vertical-align "baseline" :margin-right "5px"}
                    :on-click (fn [_]
                                (rf/dispatch [:global/notice
                                              {:message     (str "确定要删除日志 " name " 吗？")
                                               :callback-fn #(delete-log goal_id id)}]))}
                   (or earn "+0")]
                  (when name [:span.is-size-7.is-clickable
                              {:title (str "创建于：" create_at)} name])]
                 (when description [:p.is-size-7.mt-2.ml-2.has-text-grey description])])])])]))
   [:div {:style {:margin-bottom "100px"}}]])