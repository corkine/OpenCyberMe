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
    [cyberme.util.menu :refer [toggle menu]]
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
  "????????????"
  []
  (dialog :add-goal-goal!
          "????????????"
          [[:name "??????*" "????????????"]
           [:category "??????*" "????????????" {:type :select :selects GOAL-TYPE}]
           [:description "??????" "????????????" {:type :textarea :attr {:rows 2}}]
           [:progress "??????" "?????????????????????"]]
          "??????"
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
  "????????????"
  []
  (dialog :edit-goal-goal!
          "????????????"
          [[:name "??????*" "????????????"]
           [:category "??????*" "????????????" {:type :select :selects GOAL-TYPE}]
           [:description "??????" "????????????" {:type :textarea :attr {:rows 2}}]
           [:progress "??????" "?????????????????????"]]
          "??????"
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
  "??????????????????"
  []
  (dialog :add-goal-goal-log!
          "??????????????????"
          [[:name "??????*" "????????????"]
           [:description "??????" "????????????" {:type :textarea :attr {:rows 2}}]
           [:earn "??????" "???????????????????????????"]]
          "??????"
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
    [:span.title.is-clickable {:on-click (partial toggle :goal-123)}
     "????????????"]
    [:span.ml-3.is-clickable.is-size-7
     {:on-click
      #(rf/dispatch [:app/show-modal :add-goal-goal!])} "[??????]"]
    [menu {:id :goal-123}]]
   [add-goal] [add-goal-log] [edit-goal]
   (let [{data :data} @(rf/subscribe [:goal/goals-data])]
     (for [{:keys [id name info create_at update_at logs] :as goal} data] ;;?????? Goals
       ^{:key id}
       [:div.mt-5
        [:p.is-family-code.mb-1.is-clickable
         {:title (str "???????????????" create_at "\n" "???????????????" update_at)}
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
          " [??????]"]
         [:span.is-size-7
          {:on-click #(do (reset! goal-id id)
                          (rf/dispatch [:app/show-modal :add-goal-goal-log!]))}
          " [+??????]"]
         [:span.has-text-danger.is-size-7
          {:on-click
           (fn [_]
             (rf/dispatch [:global/notice
                           {:message     (str "???????????????" name "??????")
                            :callback-fn #(delete-goal id)}]))}
          " [??????]"]]
        (if-let [desp (:description info)]
          [:p.is-size-7.is-family-code.is-clickable.has-text-grey.ml-1 desp])
        (when (not-empty logs)
          [:pre.mt-2 {:style {:padding "10px 0 10px 9px" :border-radius "0"}}
           (for [{:keys [id goal_id info create_at] :as log} logs] ;;Goal ????????? Log
             ^{:key id}
             [:<>
              (let [{:keys [name description earn]} info]
                [:<>
                 [:div
                  [:div.tag.is-small.is-size-7.mr-2.is-info.is-light.is-clickable.show-delete-2
                   {:style    {:vertical-align "baseline" :margin-right "5px"}
                    :on-click (fn [_]
                                (rf/dispatch [:global/notice
                                              {:message     (str "????????????????????? " name " ??????")
                                               :callback-fn #(delete-log goal_id id)}]))}
                   (or earn "+0")]
                  (when name [:span.is-size-7.is-clickable
                              {:title (str "????????????" create_at)} name])]
                 (when description [:p.is-size-7.mt-2.ml-2.has-text-grey description])])])])]))
   [:div {:style {:margin-bottom "100px"}}]])