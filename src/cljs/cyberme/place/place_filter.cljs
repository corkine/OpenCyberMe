(ns cyberme.place.place-filter
  (:require [re-frame.core :as rf]
            [clojure.string :as string]))

(defn home-filter []
  (let [locations @(rf/subscribe [:all-location])
        labels @(rf/subscribe [:all-label])
        statuses @(rf/subscribe [:all-status])
        filter @(rf/subscribe [:filter])]
    [:div {:style {:background-color "#ffdd57"}}
     [:div.hero.is-warning.is-small.container
      [:div.hero-body
       [:div.columns
        [:div.column.is-6
         [:p.title "位置列表"]
         [:p "纳入系统管理的物的位置列表"]]
        [:div.column.is-6.has-text-right.has-text-left-mobile.mt-3
         {:style {:margin-top "-8px"
                  :z-index :0}}
         [:div
          [:div.select.is-small.is-warning.mr-2.mt-1
           [:select
            {:on-change (fn [e]
                          (let [sel-o (-> e .-target .-value)
                                mg (if (= sel-o "任何位置")
                                     (dissoc filter :location)
                                     (assoc filter :location sel-o))]
                            (rf/dispatch [:set-filter mg])
                            (reitit.frontend.easy/replace-state :home nil mg)))
             :value (or (:location filter) "")
             :id :module}
            [:option "任何位置"]
            (for [location locations]
              ^{:key location}
              [:option {:value location} location])]]
          [:div.select.is-small.is-warning.mr-2.mt-1>select
           {:on-change (fn [e] (let [sel-o (-> e .-target .-value)
                                     mg (if (= sel-o "任何标签")
                                          (dissoc filter :labels)
                                          (assoc filter :labels sel-o))]
                                 (rf/dispatch [:set-filter mg])
                                 (reitit.frontend.easy/replace-state :home nil mg)))
            :value (or (:labels filter) "")}
           [:option "任何标签"]
           (for [label labels]
             ^{:key label}
             [:option {:value label} (str "# " label)])]
          [:div.select.is-small.is-warning.mr-2.mt-1>select
           {:on-change (fn [e] (let [sel-o (-> e .-target .-value)
                                     mg (if (= sel-o "任何状态")
                                          (dissoc filter :status)
                                          (assoc filter :status sel-o))]
                                 (rf/dispatch [:set-filter mg])
                                 (reitit.frontend.easy/replace-state :home nil mg)))
            :value (or (:status filter) "")}
           [:option "任何状态"]
           (for [status statuses]
             ^{:key status}
             [:option {:value status} status])]]
         [:div.is-pulled-right.is-hidden-mobile.mr-2.mt-2
          [:p.control.has-icons-left {:style {:width :258px}}
           [:input.input.is-warning.is-small.is-rounded
            {:type      "text" :placeholder "过滤物品名称"
             :on-change (fn [e] (let [origin (-> e .-target .-value)
                                      mg (if (string/blank? origin)
                                           (dissoc filter :search)
                                           (assoc filter :search origin))]
                                  (rf/dispatch [:set-filter mg])
                                  (reitit.frontend.easy/replace-state :home nil mg)))}]
           [:span.icon.is-left
            [:i.fa.fa-search {:aria-hidden "true"}]]]]]]]]]))