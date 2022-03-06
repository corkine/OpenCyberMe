(ns cyberme.pages
  (:require
    [day8.re-frame.http-fx]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [markdown.core :refer [md->html]]
    [cyberme.event.events]
    [cyberme.about :refer [log about-page]]
    [cyberme.place.place-filter :as place-filter]
    [cyberme.place.place :as place]
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
   [place-filter/home-filter]
   (let [fetched-place-raw @(rf/subscribe [:place/fetch-data-filtered])
         fetched-place (sort-by :id fetched-place-raw)]
     (if-not (empty? fetched-place)
       [:div.container>div.content.mx-3.is-full {:style {:margin-top "0px"}}
        (for [data fetched-place]
          ^{:key (:id data)}
          [place/place-card data])]
       [:div.hero.is-small.pl-0.pr-0
        [:div.hero-body
         [:div.container.has-text-centered
          [:h3.subtitle.mt-6
           "Oops... 暂无符合条件的选项"]]]]))
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 "
          "Marvin Studio."
          " All Right Reserved.")]]])

(defn clothes-page []
  [:<>
   [top-point]
   [place-filter/home-filter]
   (let [fetched-place-raw @(rf/subscribe [:place/fetch-data-filtered])
         fetched-place (sort-by :id fetched-place-raw)]
     (if-not (empty? fetched-place)
       [:div.container>div.content.mx-3.is-full {:style {:margin-top "0px"}}
        (for [data fetched-place]
          ^{:key (:id data)}
          [place/place-card data])]
       [:div.hero.is-small.pl-0.pr-0
        [:div.hero-body
         [:div.container.has-text-centered
          [:h3.subtitle.mt-6
           "Oops... 暂无符合条件的选项"]]]]))
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 "
          "Marvin Studio."
          " All Right Reserved.")]]])

(defn foods-page []
  [:<>
   [top-point]
   [place-filter/home-filter]
   (let [fetched-place-raw @(rf/subscribe [:place/fetch-data-filtered])
         fetched-place (sort-by :id fetched-place-raw)]
     (if-not (empty? fetched-place)
       [:div.container>div.content.mx-3.is-full {:style {:margin-top "0px"}}
        (for [data fetched-place]
          ^{:key (:id data)}
          [place/place-card data])]
       [:div.hero.is-small.pl-0.pr-0
        [:div.hero-body
         [:div.container.has-text-centered
          [:h3.subtitle.mt-6
           "Oops... 暂无符合条件的选项"]]]]))
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 "
          "Marvin Studio."
          " All Right Reserved.")]]])

(defn goods-page []
  [:<>
   [top-point]
   [:selection.hero.is-large
    [:div.hero-body.has-text-centered
     [:p.title.is-family-code [:i.fa.fa-exclamation-triangle] " Coming Soon..."]
     [:p.subtitle.is-family-code  "正在施工"]]]
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 "
          "Marvin Studio."
          " All Right Reserved.")]]])

(defn package-page []
  [:<>
   [top-point]
   [:selection.hero.is-large
    [:div.hero-body.has-text-centered
     [:p.title.is-family-code  [:i.fa.fa-exclamation-triangle] " Coming Soon..."]
     [:p.subtitle.is-family-code  "正在施工"]]]
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 "
          "Marvin Studio."
          " All Right Reserved.")]]])