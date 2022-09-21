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
    [cyberme.dashboard.week-plan :as wp]
    [clojure.string :as str]))

(defn plan-page []
  [:div.mt-5.ml-4.mr-4
   [:div
    [:span.is-size-6.ml-1.my-1.is-clickable.mr-4
     {:on-click #(rf/dispatch [:common/navigate! :work])}
     "\uD83C\uDF0F 本月工作日历"]
    [:span.is-size-6.ml-1.my-1.is-clickable
     {:on-click #(rf/dispatch [:common/navigate! :work-all])}
     "\uD83C\uDF0F 工作生涯 @浪潮思科"]]
   [wp/week-plan-modify-item-dialog :dashboard/week-plan-range]
   (let [{{:keys [date result]} :data}
         @(rf/subscribe [:dashboard/week-plan-range-data])]
     (for [some-week date] ;;所有周的数据
       ^{:key some-week}
       [:div.mt-5
        [:p.is-size-4.is-family-code.ml-1.mb-1 some-week]
        (for [{:keys [id logs name category progress description last-update] :as item}
              (get result (keyword some-week))] ;;每周的每个计划
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
             {:style {:vertical-align :middle}
              :on-click (fn [_]
                          (rf/dispatch [:week-plan-db-set :modify-item item])
                          (rf/dispatch [:app/show-modal :modify-week-plan-item!]))} name]
            [:span.ml-2.is-size-7.is-family-code.is-clickable.mr-4 (str progress "%")]
            [:span.is-family-code.is-size-7.is-text-grey.is-clickable
             {:style {:opacity "0.2"}} last-update]]
           (when-not (str/blank? description)
             [:pre
              [:i.fa.fa-quote-right {:style {:float "right"
                                             :font-size "5em"
                                             :opacity "0.04"}
                                     :aria-hidden "true"}]
              description])
           (if (empty? logs)
             [:<>]
             [:pre {:style {:padding-bottom :10px}}
              (for [{:keys [id name update item-id description progress-delta] :as log} logs]
                ^{:key id}
                [:div.mb-2 ;each log
                 [:p.mb-1 ;each log's body and entity
                  [:span.ml-2.is-family-code.is-clickable
                   {:style {:vertical-align :bottom}} name]
                  [:span.ml-2.is-size-7.is-family-code.is-clickable.mr-4 (str "+" progress-delta "%")]
                  [:span.is-family-code.is-size-7.is-text-grey.is-clickable
                   {:style {:opacity "0.2"}} update]]
                 (when description
                   [:pre {:style {:padding "0em 0 0 2em"
                                  :opacity "0.5"}}
                    [:span.is-text-grey description]])])])])]))
   [:div {:style {:margin-bottom "100px"}}]])