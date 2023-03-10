(ns cyberme.dashboard.plan
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [clojure.string :as string]
    [clojure.set :as set]
    [cljs-time.core :as t]
    [cyberme.util.request :refer [ajax-flow] :as req]
    [cljs-time.format :as format]
    [reagent.core :as r]
    [cyberme.util.tool :as tool]
    [cyberme.util.menu :refer [toggle! menu]]
    [cyberme.dashboard.week-plan :as wp]
    [clojure.string :as str]))

(rf/reg-event-db
  :week-plan/trigger-url-search!
  (fn [db [_ push-state?]]
    (let [real-search-obj (:week-plan/search-obj db)]
      (if push-state?
        (reitit.frontend.easy/push-state :plan nil real-search-obj)
        (reitit.frontend.easy/replace-state :plan nil real-search-obj))
      db)))

(rf/reg-event-db
  :week-plan/search-obj-set!
  (fn [db [_ [key value]]]
    (if (:week-plan/search-obj db)
      (assoc-in db [:week-plan/search-obj key] value)
      (assoc db :week-plan/search-obj {key value}))))

(rf/reg-event-db
  :week-plan/search-obj-merge!
  (fn [db [_ new-map]]
    (if-let [old (:week-plan/search-obj db)]
      (assoc db :week-plan/search-obj (merge old new-map))
      (assoc db :week-plan/search-obj new-map))))

(rf/reg-event-db
  :week-plan/search-obj-reset!
  (fn [db [_ maps]]
    (assoc db :week-plan/search-obj maps)))

(rf/reg-sub
  :week-plan/current-range
  (fn [db _] (let [obj (:week-plan/search-obj db)]
               [(:from obj) (:to obj)])))

(defn plan-page []
  [:div.mt-5.ml-4.mr-4
   [:div
    [:span.is-size-6.ml-1.my-1.is-clickable.mr-4
     {:on-click #(rf/dispatch [:common/navigate! :work])}
     "\uD83C\uDF0F 本月工作日历"]
    [:span.is-size-6.ml-1.my-1.is-clickable
     {:on-click #(rf/dispatch [:common/navigate! :work-all])}
     "\uD83C\uDF0F 工作生涯日历"]]
   [wp/week-plan-modify-item-dialog :dashboard/week-plan-range-with-search]
   [wp/week-plan-log-update-dialog :dashboard/week-plan-range-with-search]
   [wp/week-plan-log-add-dialog :dashboard/week-plan-range-with-search]
   (let [{{:keys [date result]} :data}
         @(rf/subscribe [:dashboard/week-plan-range-data])]
     (for [some-week date]                                  ;;所有周的数据
       ^{:key some-week}
       [:div.mt-5
        [:p.is-size-4.is-family-code.ml-1.mb-1 some-week]
        (for [{:keys [id logs name category progress description last-update] :as item}
              (get result (keyword some-week))]             ;;每周的每个计划
          ^{:key id}
          [:div.mt-4
           [:p.mb-2
            [:span.tag.is-small.is-rounded.is-size-7.mr-0.is-light
             {:style {:font-variant "all-petite-caps"}
              :class (case category
                       "learn" "is-info"
                       "diet" "is-success"
                       "fitness" "is-danger"
                       "is-light")} category]
            [:span.ml-2.is-family-code.is-clickable.is-size-5
             {:style    {:vertical-align :middle}
              :title    (str "点击修改目标\n更新于：" last-update)
              :on-click #(toggle! (str "PLAN-" id))}
             name]
            [menu {:id (str "PLAN-" id) :padding :33px :padding-left :60px
                   :actions
                   [["新建日志" (fn [_]
                                  (rf/dispatch [:week-plan-db-set :current-item (assoc item :date some-week)])
                                  (rf/dispatch [:app/show-modal :add-week-plan-log!]))]
                    ["编辑项目" (fn [_]
                              (rf/dispatch [:week-plan-db-set :modify-item (assoc item :date some-week)])
                              (rf/dispatch [:app/show-modal :modify-week-plan-item!]))]
                    ["删除项目" #(rf/dispatch [:global/notice
                                               {:message  (str "是否要删除项目" name "?")
                                                :callback [[:dashboard/plan+week-plan-delete-item id]]}])]]}]
            [:span.ml-2.is-size-7.is-family-code.is-clickable.mr-4 (str progress "%")]]
           ;左侧计划描述
           [:div.columns
            (when-not (str/blank? description)
              [:div.column
               [:pre {:style {:height      "100%"
                              :white-space "pre-wrap" :word-wrap "break-word"}}
                [:i.fa.fa-quote-right {:style       {:float     "right"
                                                     :font-size "5em"
                                                     :opacity   "0.04"}
                                       :aria-hidden "true"}]
                description]])
            ;右侧日志信息
            (if (empty? logs)
              [:<>]
              [:div.column
               [:div {:style {:padding-bottom :10px :height "100%"
                              :background-color :#f5f5f5
                              :color :#4a4a4a
                              :font-size :.875em
                              :padding "1.25em 1.5em"
                              :white-space    "pre-wrap"
                              :word-wrap "break-word"}}
                (for [{:keys [id name update description progress-delta] :as log} logs]
                  ^{:key id}
                  [:div.mb-2                                ;each log
                   [:p.mb-1                                 ;each log's body and entity
                    [:span.ml-2.is-family-code.is-clickable.is-unselectable
                     {:style    {:vertical-align :bottom}
                      :on-click (partial toggle! name)
                      :title    (str "更新于：" update)} name]
                    [menu {:id name :padding :25px
                           :actions
                           [["编辑" (fn [_]
                                      (reset! wp/update-log-now (merge log {:item-id (:id item)
                                                                            :date some-week}))
                                      (rf/dispatch [:app/show-modal :update-week-plan-log!]))]
                            ["移到最前" #(rf/dispatch [:dashboard/plan+week-plan-item-move-log
                                                       (merge log {:to-start true
                                                                   :date some-week})])]
                            ["移到最后" #(rf/dispatch [:dashboard/plan+week-plan-item-move-log
                                                       (merge log {:to-end true
                                                                   :date some-week})])]
                            ["删除" #(rf/dispatch
                                       [:global/notice
                                        {:message  (str "是否要删除日志" name "?")
                                         :callback [[:dashboard/plan+week-plan-item-delete-log
                                                     [(:id item) id some-week]]]}])]]}]
                    [:span.ml-2.is-size-7.is-family-code.is-clickable.mr-4 (str "+" progress-delta "%")]]
                   (when description
                     [:pre {:style {:padding     "0em 0 0 2em"
                                    :opacity     "0.5"
                                    :white-space "pre-wrap" :word-wrap "break-word"}}
                      [:span.is-text-grey description]])])]])]])]))
   [:nav.pagination.is-centered.is-justify-content-end.pt-5.mt-5
    {:role "navigation" :aria-label "pagination"}
    [:a.pagination-previous
     {:on-click (fn [_]
                  (let [[f t] @(rf/subscribe [:week-plan/current-range])]
                    (if (<= f 4)
                      (do
                        (rf/dispatch [:week-plan/search-obj-merge!
                                      {:from 0 :to 4}])
                        (rf/dispatch [:week-plan/trigger-url-search! true])
                        (.scrollTo js/window 0 0))
                      (do
                        (rf/dispatch [:week-plan/search-obj-merge!
                                      {:from (- f 4) :to (- t 4)}])
                        (rf/dispatch [:week-plan/trigger-url-search! true])
                        (.scrollTo js/window 0 0)))))}
     "上一页"]
    [:a.pagination-next
     {:on-click (fn [_]
                  (let [[f t] @(rf/subscribe [:week-plan/current-range])]
                    (rf/dispatch [:week-plan/search-obj-merge!
                                  {:from (+ f 4) :to (+ t 4)}])
                    (rf/dispatch [:week-plan/trigger-url-search! true])
                    (.scrollTo js/window 0 0)))}
     "下一页"]]
   [:div {:style {:margin-bottom "100px"}}]])