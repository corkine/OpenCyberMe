(ns icemanager.core
  (:require
    [day8.re-frame.http-fx]
    [reagent.dom :as rdom]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [markdown.core :refer [md->html]]
    [icemanager.ajax :as ajax]
    [icemanager.events :as events]
    [icemanager.request :as req]
    [reitit.core :as reitit]
    [reitit.frontend.easy :as rfe]
    [icemanager.about :refer [log about-page]]
    [icemanager.feature-new :as feature-new]
    [icemanager.router :as share])
  (:import goog.History))

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
                 [nav-link "/" "主页" :home]
                 [nav-link "/about" "关于" :about]]
                [:div.navbar-end
                 [:div.navbar-item [feature-new/new-feature-btn]]]]]))

(defn page []
  (if-let [page @(rf/subscribe [:common/page])]
    [:div
     [navbar]
     [page]]))

(defn navigate! [match _]
  (rf/dispatch [:common/navigate match]))

(defn start-router! []
  (rfe/start!
    #_router
    (reitit/router (share/share-router))
    navigate!
    {:use-fragment false}))

(defn ^:dev/after-load mount-components []
  (rf/clear-subscription-cache!)
  (rdom/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (start-router!)
  (ajax/load-interceptors!)
  (mount-components))
