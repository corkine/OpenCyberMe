(ns cyberme.router
  (:require
    #?@(:clj  [[cyberme.middleware :as middleware]
               [cyberme.routes.home :as home]]
        :cljs [[spec-tools.data-spec :as ds]
               [re-frame.core :as rf]
               [cyberme.pages :as core]
               [cyberme.about :as about]
               [cyberme.util.storage :as storage]])))

(defn share-router []
  ["" #?(:clj {:middleware [middleware/wrap-csrf
                            middleware/wrap-formats]
               :get        home/home-page})
   ["/"
    (merge {:name :dashboard}
           #?(:cljs {:view        #'core/dashboard-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:user/fetch-from-local])
                                             (rf/dispatch [:dashboard/recent])
                                             (rf/dispatch [:dashboard/day-work]))}]}))]

   ["/properties"
    (merge {:name :properties}
           #?(:cljs {:view        #'core/properties-page
                     :controllers [{:parameters {:query [:status :location :labels]}
                                    :start      (fn [{query :query}]
                                                  (if (or (nil? query) (empty? query))
                                                    (let [data-db (storage/get-item "good-filter")]
                                                      (rf/dispatch [:set-filter data-db])
                                                      (reitit.frontend.easy/replace-state :properties nil data-db))
                                                    (rf/dispatch [:set-filter query]))
                                                  (rf/dispatch [:user/fetch-from-local])
                                                  (rf/dispatch [:place/fetch])
                                                  (rf/dispatch [:recent/fetch]))}]}))]

   #_["/clothes"
    (merge {:name :clothes}
           #?(:cljs {:view        #'core/clothes-page
                     :controllers [{:parameters {:query [:status :location :labels]}
                                    :start      (fn [{query :query}]
                                                  (if (or (nil? query) (empty? query))
                                                    (let [data-db (storage/get-item "good_filter")]
                                                      (rf/dispatch [:set-filter data-db])
                                                      (reitit.frontend.easy/replace-state :properties nil data-db))
                                                    (rf/dispatch [:set-filter query]))
                                                  (rf/dispatch [:user/fetch-from-local])
                                                  (rf/dispatch [:place/fetch])
                                                  (rf/dispatch [:recent/fetch]))}]}))]

   ["/work"
    (merge {:name :work}
           #?(:cljs {:view        #'core/hcm-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:user/fetch-from-local])
                                             (rf/dispatch [:hcm/month])
                                             (rf/dispatch [:hcm/todo]))}]}))]

   ["/diary"
    (merge {:name :diary}
           #?(:cljs {:view        #'core/diary-page
                     :controllers [{:parameters {:query [:labels :contains]}
                                    :start      (fn [{query :query}]
                                                  (if (or (nil? query) (empty? query))
                                                    (let [data-db (storage/get-item "diary_filter")]
                                                      (rf/dispatch [:diary/set-filter data-db])
                                                      (reitit.frontend.easy/replace-state :diary nil data-db))
                                                    (rf/dispatch [:diary/set-filter query]))
                                                  (rf/dispatch [:user/fetch-from-local])
                                                  (rf/dispatch [:diary/current-data-clean])
                                                  (rf/dispatch [:diary/list]))}]}))]

   ["/diary-new"
    (merge {:name :diary-new}
           #?(:cljs {:view        #'core/diary-new-page
                     :controllers [{:parameters {:query []}
                                    :start      (fn [_]
                                                  (rf/dispatch [:user/fetch-from-local]))}]}))]

   ["/diary/:id/edit"
    (merge {:name :diary-edit}
           #?(:cljs {:view        #'core/diary-edit-page
                     :controllers [{:parameters {:path [:id]}
                                    :start      (fn [{path :path}]
                                                  (rf/dispatch [:user/fetch-from-local])
                                                  (rf/dispatch [:diary/current (:id path)]))}]}))]

   ["/diary/:id"
    (merge {:name :diary-view}
           #?(:cljs {:view        #'core/diary-view-page
                     :controllers [{:parameters {:path [:id]}
                                    :start      (fn [{path :path}]
                                                  (rf/dispatch [:user/fetch-from-local])
                                                  (rf/dispatch [:diary/current (:id path)]))}]}))]

   ["/goods"
    (merge {:name :goods}
           #?(:cljs {:view        #'core/goods-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:user/fetch-from-local]))}]}))]

   ["/package"
    (merge {:name :package}
           #?(:cljs {:view        #'core/package-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:user/fetch-from-local]))}]}))]

   ["/about"
    (merge {:name :about}
           #?(:cljs {:view        #'about/about-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:user/fetch-from-local])
                                             (rf/dispatch [:fetch-usage])
                                             (rf/dispatch [:fetch-wishlist]))}]}))]

   ["/demo"
    (merge {:name :demo}
           #?(:cljs {:view        #'core/demo-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:user/fetch-from-local]))}]}))]

   ["/cook"
    (merge {:name :cook}
           #?(:cljs {:view        #'core/cook-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:user/fetch-from-local]))}]}))]])