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
                     :controllers [{:parameters {:query [:status :location :labels]}
                                    :start      (fn [{query :query}]
                                                  (rf/dispatch [:place/fetch])
                                                  (rf/dispatch [:recent/fetch])
                                                  (rf/dispatch [:set-filter query]))}]}))]

   ["/foods"
    (merge {:name :foods}
           #?(:cljs {:view        #'core/foods-page
                     :controllers [{:parameters {:query [:status :location :labels]}
                                    :start      (fn [{query :query}]
                                                  (rf/dispatch [:place/fetch])
                                                  (rf/dispatch [:recent/fetch])
                                                  (rf/dispatch [:set-filter query]))}]}))]

   ["/clothes"
    (merge {:name :clothes}
           #?(:cljs {:view        #'core/clothes-page
                     :controllers [{:parameters {:query [:status :location :labels]}
                                    :start      (fn [{query :query}]
                                                  (rf/dispatch [:place/fetch])
                                                  (rf/dispatch [:recent/fetch])
                                                  (rf/dispatch [:set-filter query]))}]}))]

   ["/goods"
    (merge {:name :goods}
           #?(:cljs {:view        #'core/goods-page
                     :controllers [{:start (fn [_])}]}))]

   ["/package"
    (merge {:name :package}
           #?(:cljs {:view        #'core/package-page
                     :controllers [{:start (fn [_])}]}))]

   ["/about"
    (merge {:name :about}
           #?(:cljs {:view        #'about/about-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:fetch-usage])
                                             (rf/dispatch [:fetch-wishlist]))}]}))]])