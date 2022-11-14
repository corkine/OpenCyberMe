(ns cyberme.util.menu
  (:require [re-frame.core :as rf]))

(rf/reg-event-db
  :show-context-menu
  (fn [db [_ id]]
    (assoc-in db [:showing-context-menu id] true)))

(rf/reg-event-db
  :hide-context-menu
  (fn [db [_ id]]
    (dissoc db :showing-context-menu id)))

(rf/reg-event-db
  :toggle-context-menu
  (fn [db [_ id]]
    (if (true? (get-in db [:showing-context-menu id]))
      (dissoc db :showing-context-menu id)
      (assoc-in db [:showing-context-menu id] true))))

(rf/reg-event-db
  :open-my-close-others-or-close-all
  (fn [db [_ id]]
    (if (true? (get-in db [:showing-context-menu id]))
      (dissoc db :showing-context-menu)
      (assoc db :showing-context-menu {id true}))))

(rf/reg-sub
  :context-menu-showing?
  (fn [db [_ id]]
    (true? (get-in db [:showing-context-menu id]))))

(defn menu
  "必须传入 actions [string func] 下拉菜单项目
  可传入 padding 表示下拉菜单距离此项目的浮动后偏移"
  [{:keys [id padding actions] :or {actions []}}]
  (let [showing? @(rf/subscribe [:context-menu-showing? id])]
    [(if showing? :div.dropdown.is-active :div.dropdown)
     {:style {:float :left :top (or padding :40px)}}
     [:div#dropdown-menu3.dropdown-menu
      {:role "menu"
       :on-click #(rf/dispatch [:hide-context-menu id])}
      (if (empty? actions)
        [:div.dropdown-content
         [:a.dropdown-item "Oops... Nothing Here"]]
        [:div.dropdown-content
         ;[:hr.dropdown-divider]
         (for [[k f] actions]
           ^{:key k}
           [:a.dropdown-item {:on-click f} k])])]]))

(defn toggle [menu-id]
  (rf/dispatch [:toggle-context-menu menu-id]))

(defn toggle! [menu-id]
  (rf/dispatch [:open-my-close-others-or-close-all menu-id]))