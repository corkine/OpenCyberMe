(ns icemanager.router
  (:require
    #?@(:clj  [[icemanager.middleware :as middleware]
               [icemanager.routes.home :as home]]
        :cljs [[spec-tools.data-spec :as ds]
               [re-frame.core :as rf]
               [icemanager.pages :as core]
               [icemanager.about :as about]])))

(defn share-router []
  ["" #?(:clj {:middleware [middleware/wrap-csrf
                            middleware/wrap-formats]
               :get home/home-page})
   ["/"
    (merge {:name :home}
           #?(:cljs {:view        #'core/home-page
                     :controllers [{:parameters {:query [:status :contains :version]}
                                    :start      (fn [{query :query}]
                                                  (rf/dispatch [:place/fetch])
                                                  (rf/dispatch [:set-filter query])
                                                  (rf/dispatch [:fetch-features]))}]}))]
   ["/feature/:rs-id/edit"
    (merge {:name :feature}
           #?(:cljs {:view        #'core/feature-page
                     :controllers [{:parameters {:path [:rs-id]}
                                    :start      (fn [{{:keys [rs-id]} :path}]
                                                  (rf/dispatch [:fetch-feature rs-id]))}]}))
    ]
   ["/feature/:rs-id/"
    (merge {:name :feature-view}
           #?(:cljs {:view        #'core/feature-view-page
                     :controllers [{:parameters {:path  [:rs-id]
                                                 :query [:go]}
                                    :start      (fn [{{:keys [rs-id]} :path
                                                      {:keys [go]}    :query}]
                                                  (rf/dispatch [:set-view-go go])
                                                  (rf/dispatch [:fetch-feature rs-id]))
                                    :stop       (fn [_]
                                                  (rf/dispatch [:clean-current-feature])
                                                  (rf/dispatch [:clean-view-go]))}]}))
    ]
   ["/about"
    (merge {:name :about}
           #?(:cljs {:view        #'about/about-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:fetch-usage])
                                             (rf/dispatch [:fetch-wishlist]))}]}))]])