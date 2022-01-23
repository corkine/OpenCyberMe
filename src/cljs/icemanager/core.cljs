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
   [feature/feature-filter]
   (let [feature-filtered @(rf/subscribe [:get-filtered-features])]
     (if-not (empty? feature-filtered)
       [:section.section>div.container>div.content {:style {:margin-top "-40px"}}
        (for [data feature-filtered]
          ^{:key (:id data)}
          [feature/feature-card data {:with-footer      true
                                      :with-description true
                                      :with-edit        true}])]
       [:div.hero.is-small.pl-0.pr-0
        [:div.hero-body
         [:div.container.has-text-centered
          [:h3.subtitle.mt-6
           "Oops... 暂无符合条件的特性"]]]]))])

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
  (let [feature-data @(rf/subscribe [:current-feature])
        go @(rf/subscribe [:view-go])]
    [:<>
     [feature-view/feature-view-content feature-data go]]))

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
                          :start      (fn [{{:keys [status contains version] :as query} :query}]
                                        (rf/dispatch [:set-filter query])
                                        (rf/dispatch [:fetch-features]))}]}]
     ["/feature/:rs-id/edit" {:name        :feature
                              :view        #'feature-page
                              :controllers [{:parameters {:path [:rs-id]}
                                             :start      (fn [{{:keys [rs-id]} :path}]
                                                           (rf/dispatch [:fetch-feature rs-id]))}]}]
     ["/feature/:rs-id/" {:name        :feature-view
                          :view        #'feature-view-page
                          :controllers [{:parameters {:path  [:rs-id]
                                                      :query [:go]}
                                         :start      (fn [{{:keys [rs-id]} :path
                                                           {:keys [go]}    :query}]
                                                       (rf/dispatch [:set-view-go go])
                                                       (rf/dispatch [:fetch-feature rs-id]))
                                         :stop       (fn [_] (rf/dispatch [:clean-view-go]))}]}]
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
