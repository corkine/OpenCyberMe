(ns icemanager.core
  (:require
    [day8.re-frame.http-fx]
    [reagent.dom :as rdom]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [goog.events :as events]
    [goog.history.EventType :as HistoryEventType]
    [markdown.core :refer [md->html]]
    [icemanager.ajax :as ajax]
    [icemanager.events]
    [reitit.core :as reitit]
    [reitit.frontend.easy :as rfe]
    [clojure.string :as string]
    [icemanager.feature :as feature]
    [icemanager.modals :as modals]
    [icemanager.about :refer [log about-page]]
    [icemanager.feature-new :as feature-new]
    [icemanager.feature-view :as feature-view]
    [icemanager.feature-edit :as feature-edit]
    [icemanager.request :as req])
  (:import goog.History))

(defn top-point []
  (r/create-class
    {:component-did-mount
     (fn [this]
       (.scrollTo js/window 0 0)
       #_(when true (.scrollIntoView (rdom/dom-node this) true)))
     :reagent-render
     (fn [_] [:div ""])}))

(defn nav-link [uri title page]
  [:a.navbar-item
   {:href  uri
    :class (when (= page @(rf/subscribe [:common/page])) :is-active)}
   title])

(defn navbar []
  (r/with-let [expanded? (r/atom false)]
              [:nav.navbar.is-info>div.container
               [:div.navbar-brand
                [:a.navbar-item {:href "/" :style {:font-weight :bold}} "ICE 特性管理器"]
                [:span.navbar-burger.burger
                 {:data-target :nav-menu
                  :on-click    #(swap! expanded? not)
                  :class       (when @expanded? :is-active)}
                 [:span] [:span] [:span]]]
               [:div#nav-menu.navbar-menu
                {:class (when @expanded? :is-active)}
                [:div.navbar-start
                 [nav-link "#/" "主页" :home]
                 [nav-link "#/about" "关于" :about]]
                [:div.navbar-end
                 [:div.navbar-item [feature-new/new-feature-btn]]]]]))

(defn home-page []
  [:<>
   [top-point]
   (let [versions @(rf/subscribe [:all-version])
         developers @(rf/subscribe [:all-developer])
         statuses @(rf/subscribe [:all-status])
         select @(rf/subscribe [:filter])]
     [:div.hero.is-warning.is-small
      [:div.hero-body
       [:p.title "ICE 特性列表"]
       [:div.columns
        [:div.column.is-6
         [:p "纳入系统管理的 ICE 特性列表"]]
        [:div.column.is-6.has-text-right.has-text-left-mobile
         {:style {:margin-top "-8px"}}
         [:div.select.is-small.is-warning.mr-2.mt-1>select
          {:on-click (fn [e]
                       (let [sel-o (-> e .-target .-value)
                             mg (if (= sel-o "所有版本")
                                  (dissoc select :version)
                                  (assoc select :version sel-o))]
                         (rf/dispatch [:set-filter mg])
                         (reitit.frontend.easy/replace-state :home nil mg)))
           :value (or (:version select) "")
           :on-change (fn [_])}
          [:option "所有版本"]
          (for [version versions]
            ^{:key version}
            [:option {:value version} version])]
         [:div.select.is-small.is-warning.mr-2.mt-1>select
          {:on-click (fn [e] (let [sel-o (-> e .-target .-value)
                                   mg (if (= sel-o "任意状态")
                                        (dissoc select :status)
                                        (assoc select :status sel-o))]
                               (rf/dispatch [:set-filter mg])
                               (reitit.frontend.easy/replace-state :home nil mg)))
           :value (or (:status select) "")
           :on-change (fn [_])}
          [:option "任意状态"]
          (for [status statuses]
            ^{:key status}
            [:option {:value status} status])]
         [:div.select.is-small.is-warning.mr-2.mt-1>select
          {:on-click (fn [e] (let [sel-o (-> e .-target .-value)
                                   mg (if (= sel-o "任意人员")
                                        (dissoc select :contains)
                                        (assoc select :contains sel-o))]
                               (rf/dispatch [:set-filter mg])
                               (reitit.frontend.easy/replace-state :home nil mg)))
           :value (or (:contains select) "")
           :on-change (fn [_])}
          [:option "任意人员"]
          (for [developer developers]
            ^{:key developer}
            [:option {:value developer} (str "包含:" developer)])]]]]])
   [:section.section>div.container>div.content {:style {:margin-top "-40px"}}
    (for [data @(rf/subscribe [:get-filtered-features])]
      ^{:key (:id data)}
      [feature/feature-card data {:with-footer      true
                                  :with-description true
                                  :with-edit        true}])]])

(defn feature-page []
  (let [feature-data @(rf/subscribe [:current-feature])]
    [:<>
     [:div.hero.is-success.is-small
      {:style {:padding-left   :30px
               :padding-bottom :30px}}
      [feature/feature-card feature-data {:with-footer      false
                                          :with-description true}]]
     [:section.section>div.container>div.content
      [feature-edit/feature-form feature-data]]]))

(defn feature-view-page []
  (let [feature-data @(rf/subscribe [:current-feature])]
    [:<>
     [top-point]
     [feature-view/feature-view-content feature-data]]))

(defn page []
  (if-let [page @(rf/subscribe [:common/page])]
    [:div
     [navbar]
     [page]]))

(defn navigate! [match _]
  (rf/dispatch [:common/navigate match]))

(def router
  (reitit/router
    [["/" {:name        :home
           :view        #'home-page
           :controllers [{:parameters {:query [:status :contains :version]}
                          :start (fn [{{:keys [status contains version] :as query} :query}]
                                   (rf/dispatch [:set-filter query])
                                   (rf/dispatch [:fetch-features]))}]}]
     ["/feature/:rs-id/edit" {:name        :feature
                              :view        #'feature-page
                              :controllers [{:parameters {:path [:rs-id]}
                                             :start      (fn [{{:keys [rs-id]} :path}]
                                                           (rf/dispatch [:fetch-feature rs-id]))}]}]
     ["/feature/:rs-id/" {:name        :feature-view
                          :view        #'feature-view-page
                          :controllers [{:parameters {:path [:rs-id]}
                                         :start      (fn [{{:keys [rs-id]} :path}]
                                                       (rf/dispatch [:fetch-feature rs-id]))}]}]
     ["/about" {:name        :about
                :view        #'about-page
                :controllers [{:start (fn [_]
                                        (rf/dispatch [:fetch-usage])
                                        (rf/dispatch [:fetch-wishlist]))}]}]]))

(defn start-router! []
  (rfe/start!
    router
    navigate!
    {}))

(defn ^:dev/after-load mount-components []
  (rf/clear-subscription-cache!)
  (rdom/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (start-router!)
  (ajax/load-interceptors!)
  (mount-components))
