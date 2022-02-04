(ns icemanager.pages
  (:require
    [day8.re-frame.http-fx]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [markdown.core :refer [md->html]]
    [icemanager.events]
    [icemanager.feature :as feature]
    [icemanager.about :refer [log about-page]]
    [icemanager.feature-view :as feature-view]
    [icemanager.feature-edit :as feature-edit]
    [icemanager.place :as place]
    [icemanager.fake :as fake]
    [clojure.string :as string]))

(defn top-point []
  (r/create-class
    {:component-did-mount
     (fn [this]
       (.scrollTo js/window 0 0)
       #_(when true (.scrollIntoView (rdom/dom-node this) true)))
     :reagent-render
     (fn [_] [:div ""])}))

(defn home-page []
  [:<>
   [top-point]
   [feature/home-filter]
   (let [feature-filtered @(rf/subscribe [:get-filtered-features])]
     (if-not (empty? feature-filtered)
       [:div.container>div.content.mx-3.is-full {:style {:margin-top "0px"}}
        (for [data feature-filtered]
          ^{:key (:id data)}
          [place/place-card fake/fake-place-data])]
       [:div.hero.is-small.pl-0.pr-0
        [:div.hero-body
         [:div.container.has-text-centered
          [:h3.subtitle.mt-6
           "Oops... 暂无符合条件的位置"]]]]))])

(defn feature-page []
  (let [feature-data @(rf/subscribe [:current-feature])]
    [:<>
     [top-point]
     [:div {:style {:background-color :#48c774}}
      [:div.hero.is-success.is-small.container.is-fullhd
       {:style {:padding-left   :30px
                :padding-bottom :30px}}
       [feature/feature-card feature-data {:with-footer      false
                                           :with-description true}]]]
     [:section.section>div.container>div.content
      [feature-edit/feature-form feature-data]]]))

(defn feature-view-page []
  (let [feature-data @(rf/subscribe [:current-feature])
        go @(rf/subscribe [:view-go])]
    [:<>
     [feature-view/feature-view-content feature-data go]]))