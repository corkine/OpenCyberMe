(ns icemanager.core
  (:require
    [day8.re-frame.http-fx]
    [reagent.dom :as rdom]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [markdown.core :refer [md->html]]
    [reitit.core :as reitit]
    [reitit.frontend.easy :as rfe]
    [icemanager.event.ajax :as ajax]
    [icemanager.event.events :as events]
    [icemanager.event.request :as req]
    [icemanager.place.place-new :as place-new]
    [icemanager.place.place-edit :as place-edit]
    [icemanager.good.package-new :as package-new]
    [icemanager.good.good-new :as good-new]
    [icemanager.good.good-edit :as good-edit]
    [icemanager.about :refer [log about-page]]
    [icemanager.router :as share]
    [icemanager.modals :as modals])
  (:import goog.History))

(defn nav-link [uri title page]
  [:a.navbar-item
   {:href  uri
    :class (when (= page @(rf/subscribe [:common/page])) :is-active)}
   title])

(defn global-info []
  (let [{:keys [message callback]} @(rf/subscribe [:global/notice])]
    [modals/modal-card :notice "提示"
     [:p.has-text-black message]
     [:button.button.is-primary.is-fullwidth
      {:on-click (fn [_]
                   (rf/dispatch [:global/notice-clean])
                   (when-not (nil? callback)
                     (if (vector? (first callback))
                       (doseq [c callback]
                         (rf/dispatch c))
                       (do
                         (rf/dispatch callback))))
                   (rf/dispatch [:app/hide-modal :notice]))}
      "确定"]
     #(rf/dispatch [:global/notice-clean])]))

(defn navbar []
  (r/with-let [expanded? (r/atom false)]
              [:nav.navbar.is-info {:style {:z-index :5}}
               ;移动设备place-edit穿模，select 箭头 z-index 为 4，这里要设置为5
               ;默认 navbar 应该很高 z-index，其导致菜单被遮盖，5 的设置避免了此问题
               [:div.container
                [global-info]
                [place-edit/place-edit-holder]
                [good-edit/edit-good-holder]
                [:div.navbar-brand
                 [:a.icon-text.navbar-item.has-text-white {:href "/"}
                  [:span.icon [:i.fa.fa-ravelry.mr-1]]
                  [:span.is-family-monospace.has-text-bold.is-size-4.has-text-white "管家"]]
                 [:span.navbar-burger.burger
                  {:data-target :nav-menu
                   :on-click    #(swap! expanded? not)
                   :class       (when @expanded? :is-active)}
                  [:span] [:span] [:span]]]
                [:div#nav-menu.navbar-menu
                 {:class (when @expanded? :is-active)}
                 [:div.navbar-start
                  [nav-link "/" "资产" :home]
                  [nav-link "/clothes" "衣物" :clothes]
                  [nav-link "/foods" "食品耗材" :foods]
                  [nav-link "/goods" "所有物品" :goods]
                  [nav-link "/package" "打包" :package]
                  [nav-link "/about" "关于" :about]]
                 [:div.navbar-end {:style {:margin-right :15px}}
                  [:div.navbar-item.px-1
                   [place-new/new-place-btn]]
                  [:div.navbar-item.px-1
                   [package-new/new-package-btn]]
                  [:div.navbar-item.px-1
                   [good-new/new-good-btn]]]]]]))

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
