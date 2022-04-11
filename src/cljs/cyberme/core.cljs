(ns cyberme.core
  (:require
    [day8.re-frame.http-fx]
    [reagent.dom :as rdom]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [markdown.core :refer [md->html]]
    [reitit.core :as reitit]
    [reitit.frontend.easy :as rfe]
    [cyberme.util.ajax :as ajax]
    [cyberme.util.events :as events]
    [cyberme.util.request :as req]
    [cyberme.place.request :as req-place]
    [cyberme.good.request :as req-good]
    [cyberme.work.request :as req-work]
    [cyberme.diary.request :as req-diary]
    [cyberme.dashboard.request :as req-dash]
    [cyberme.diary.request :as req-daily]
    [cyberme.place.new :as place-new]
    [cyberme.place.edit :as place-edit]
    [cyberme.good.package-new :as package-new]
    [cyberme.good.new :as good-new]
    [cyberme.good.edit :as good-edit]
    [cyberme.about :refer [log about-page]]
    [cyberme.router :as share]
    [cyberme.modals :as modals]
    [cyberme.login.core :as login])
  (:import goog.History))

(defn nav-link [uri title page]
  [:a.navbar-item
   {:href  uri
    :class (when (= page @(rf/subscribe [:common/page])) :is-active)}
   title])

(defn global-info []
  (let [{:keys [message pre-message callback callback-fn]} @(rf/subscribe [:global/notice])]
    [modals/modal-card :notice "提示"
     [:<>
      (when pre-message
        [:pre.has-text-black pre-message])
      (when message
        [:p.has-text-black message])]
     [:button.button.is-primary.is-fullwidth
      {:on-click (fn [_]
                   (rf/dispatch [:global/notice-clean])
                   (when-not (nil? callback)
                     (if (vector? (first callback))
                       (doseq [c callback]
                         (rf/dispatch c))
                       (do
                         (rf/dispatch callback))))
                   (when-not (nil? callback-fn)
                     (callback-fn))
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
                [:div {:style {:width :0px :height :0px :overflow :hidden}}
                 [place-new/new-place-btn]
                 [package-new/new-package-btn]
                 [good-new/new-good-btn]
                 [login/login-button]]
                [:div.navbar-brand
                 [:a.icon-text.navbar-item.has-text-white {:href "/"}
                  [:span.icon [:i.fa.fa-ravelry.mr-1]]
                  [:span.has-text-bold.is-size-5.has-text-white "CyberMe"]]
                 [:span.navbar-burger.burger
                  {:data-target :nav-menu
                   :on-click    #(swap! expanded? not)
                   :class       (when @expanded? :is-active)}
                  [:span] [:span] [:span]]]
                [:div#nav-menu.navbar-menu
                 {:class (when @expanded? :is-active)}
                 [:div.navbar-start
                  [nav-link "/" "总览" :dashboard]
                  [nav-link "/work" "工作" :work]
                  [nav-link "/diary" "日记" :diary]
                  #_[nav-link "/cook" "厨记" :cook]
                  [nav-link "/properties" "物品" :properties]
                  [nav-link "/clothes" "衣物" :clothes]
                  #_[nav-link "/foods" "耗材" :foods]
                  [nav-link "/about" "关于" :about]]
                 [:div.navbar-end {:style {:margin-right :15px}}
                  [:div.navbar-item.has-dropdown.is-hoverable.mx-0
                   [:a.navbar-link "操作"]
                   [:div.navbar-dropdown.is-boxed
                    [:a.navbar-item
                     {:on-click #(rf/dispatch [:app/show-modal :create-new-place])}
                     "新建位置"]
                    [:a.navbar-item
                     {:on-click #(rf/dispatch [:app/show-modal :create-new-package])}
                     "新建打包"]
                    [:a.navbar-item
                     {:on-click #(rf/dispatch [:app/show-modal :create-new-good])}
                     "物品入库"]
                    [:a.navbar-item
                     {:on-click #(rf/dispatch [:note/last])}
                     "最近笔记"]]]
                  (let [{login-hint :user} @(rf/subscribe [:api-auth])
                        login-hint (or login-hint "登录")]
                    [:div.navbar-item.mx-0
                     [:div.is-clickable
                      {:on-click #(rf/dispatch [:app/show-modal :login-info-set])}
                      [:span.icon-text
                       [:span.icon [:i.fa.fa-user {:style {:margin-left :-10px}}]]
                       [:span {:style {:margin-left :-5px}} login-hint]]]
                     #_[:button.button.is-info
                        {:on-click #(rf/dispatch [:app/show-modal :login-info-set])}
                        [:span.icon-text
                         [:span.icon [:i.fa.fa-user {:style {:margin-right :-10px}}]]
                         [:span " 登录"]]]])]]]]))

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
