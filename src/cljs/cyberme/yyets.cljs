(ns cyberme.yyets
  (:require [clojure.string :as str]
            [cyberme.util.request :refer [ajax-flow]]
            [cyberme.util.upload :as upload]
            [goog.string :as gstring]
            [reagent.core :as r]
            [re-frame.core :as rf]))

(ajax-flow {:call           :yyets/resource
            :uri-fn         #(str "/cyber/movie/yyets/resource/" %)
            :data           :yyets/resource-data
            :clean          :yyets/resource-clean
            :failure-notice true})

(rf/reg-event-db
  :yyets/set-select
  (fn [db [_ [season fmt]]]
    (assoc-in db [:yyets/select season] fmt)))

(rf/reg-sub
  :yyets/select-kind
  (fn [db [_ season]]
    (get-in db [:yyets/select season] nil)))

(rf/reg-event-db
  :yyets/session-open-toggle!
  (fn [db [_ session]]
    (let [old (get db :yyets/open-session #{})]
      (assoc db :yyets/open-session ((if (contains? old session) disj conj) old session)))))

(rf/reg-sub
  :yyets/is-session-open?
  (fn [db [_ session-cn]]
    (contains? (get db :yyets/open-session #{})
               session-cn)))

(rf/reg-event-db
  :yyets/remove-temp-data!
  (fn [db _]
    (-> db
        (dissoc :yyets/open-session)
        (dissoc :yyets/select))))

(defn resource-page []
  (let [res-data @(rf/subscribe [:yyets/resource-data])
        ;info :cnname :channel :expire :aliasname :year[] :id :show_type :area :enname :views
        ;list :season_num :season_cn :format[]
        ;     :items {format [{:name :size :itemid :episode :dateline :yyets_trans
        ;                      :files [:way :passwd :way_cn :address]}]}
        {:keys [info list]} (-> res-data :data)
        {:keys [cnname channel year id area enname aliasname show_type]} info]
    (if (nil? info)
      [:div.container {:style {:margin "32px 0 32px 32px"}} "正在加载..."]
      [:div
       [:div.container {:style {:margin "32px 0 32px 32px"}}
        [:p.mb-1
         [:span.title.mr-2 (str cnname)]
         [:span.subtitle.mr-2 (str enname)]
         [:span.subtitle.mr-2 (str year)]]
        [:p [:span.mr-2 aliasname]]
        [:p
         [:span.tag.is-small.is-light.is-info.mr-1 area]
         [:span.tag.is-small.is-light.is-info.mr-1 (str/upper-case channel)]
         [:span.tag.is-small.is-light.is-info.mr-1 (str "ID #" id)]]]
       (doall
         (for [{:keys [season_num season_cn formats items]} list]
           ^{:key season_cn}
           [:div {:style {:margin "18px 0 0 32px"}}
            (let [session-open? @(rf/subscribe [:yyets/is-session-open? season_cn])]
              [:<>
               [:p.is-clickable {:on-click #(rf/dispatch [:yyets/session-open-toggle! season_cn])}
                (if session-open?
                  [:i.fa.fa-chevron-down.mr-2 {:aria-hidden "true"}]
                  [:i.fa.fa-chevron-right.mr-2 {:aria-hidden "true"}])
                [:span.subtitle season_cn]]
               (when-not session-open?
                 [:hr.mt-0])
               (when session-open?
                 [:<>
                  (let [active-format @(rf/subscribe [:yyets/select-kind season_num])
                        active-format (or active-format (first formats))
                        active-format-kw (keyword active-format)
                        ;name size files [] itemid episode dateline yyets_trans
                        items-this-format (get items active-format-kw)]
                    [:div
                     [:div.tabs {:style {:margin "-24px 0 10px -32px"}}
                      [:ul
                       (for [format formats]
                         ^{:key (str season_cn "-" format)}
                         [:<>
                          (if (= format active-format)
                            [:li.is-active [:a format]]
                            [:li [:a {:on-click #(rf/dispatch [:yyets/set-select [season_num format]])} format]])])]]
                     [:table.table.is-hoverable {:style {:margin-bottom "30px"}}
                      [:tbody.mb-5
                       (for [{:keys [itemid name size files episode dateline yyets_trans]} (or items-this-format [])]
                         ^{:key itemid}
                         [:tr
                          [:td {:style {:vertical-align :middle}} name]
                          [:td {:style {:vertical-align :middle}} size]
                          [:td {:style {:text-align :right}}
                           (for [{:keys [way passwd way_cn address] :as file} files]
                             ^{:key (str way_cn "-" address)}
                             [:button.button.is-small
                              {:style    {:margin "1px 2px 1px 0"}
                               :title    (str address "\n" passwd)
                               :on-click #(if (str/starts-with? (or address "") "http")
                                            (.open js/window address "_blank")
                                            (do (.writeText (.-clipboard js/navigator) address)
                                                (rf/dispatch [:global/notice {:message "已将地址复制到剪贴板"}])))}
                              way_cn])]])
                       ]]])])])]))])))
