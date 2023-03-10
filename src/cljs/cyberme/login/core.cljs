(ns cyberme.login.core
  (:require [re-frame.core :as rf]
            [cyberme.modals :as modals]
            [reagent.core :as r]
            [clojure.string :as string]
            [cyberme.util.tool :as tool]
            [cyberme.util.storage :as storage]
            [clojure.string :as str]))

(defn login-button []
  (r/with-let
    [fields (r/atom {})
     errors (r/atom {})]
    (let [{username :user-display} @(rf/subscribe [:api-auth])
          _ (when username (swap! fields assoc :username username))]
      (letfn [(common-fields [id label hint {:keys [type attr selects]}]
                (r/with-let [v (r/cursor fields [id])
                             e (r/cursor errors [id])]
                            [:div.field
                             [:label.label {:for id} label " "
                              [:span.has-text-grey-light.is-size-7.has-text-weight-light
                               (str hint)]]
                             [(cond (= type :textarea) :textarea.textarea
                                    (= type :select) :div.select
                                    :else :input.input)
                              (merge (if (= type :select)
                                       {:id id}
                                       {:id        id
                                        :type      (if type type :text)
                                        :value     @v
                                        :on-change #(reset! v (.. % -target -value))})
                                     (if attr attr {}))
                              (when-not (nil? selects)
                                [:select
                                 {:id        id
                                  :value     (or @v "")
                                  :on-change #(reset! v (.. % -target -value))}
                                 (for [k selects]
                                   ^{:key k}
                                   [:option {:value k} k])])]
                             (when-let [message @e]
                               [:p.help.is-danger message])]))
              (submit-feature-add []
                (let [data @fields
                      username (:username data)
                      password (:password data)
                      session (:session data)]
                  (reset! errors {})
                  (cond (str/blank? username) (swap! errors assoc :username "????????????????????????")
                        (str/blank? password) (swap! errors assoc :password "?????????????????????")
                        :else (do
                                (reset! fields {})
                                (reset! errors {})
                                (storage/set-item "api_auth"
                                                  {:username  username
                                                   :password
                                                   (tool/pass-encode
                                                     password
                                                     (case session
                                                           "5??????" (* 60 5)
                                                           "1??????" (* 60 60)
                                                           "1???" (* 60 60 24)
                                                           "1???" (* 60 60 24 7)
                                                           "1???" (* 60 60 24 30)
                                                           "1???" (* 60 60 24 365)
                                                           (* 60 5)))
                                                   :update_at (str (js/Date.))})
                                (rf/dispatch [:app/hide-modal :login-info-set])
                                (rf/dispatch [:user/fetch-from-local])
                                (.reload js/location)))))]
        (modals/modal-button
          :login-info-set
          {:button    {:class ["is-info"]}
           :fix-title "??????"}
          [:i.fa.fa-user {:style {:margin-right :-10px}}]
          (if username "??????????????????" "??????")
          [:div {:style {:color "black"}}
           [common-fields :username "?????????" "???????????????????????????????????????????????????????????? API ??????"]
           [common-fields :password "??????" "?????????????????????????????????????????????????????????????????????????????????"
            {:type :password}]
           [common-fields :session "????????????" "???????????????????????????"
            {:type :select :selects ["5??????" "1??????" "1???" "1???" "1???" "1???"]}]]
          [:button.button.is-primary.is-fullwidth
           {:on-click (fn [_] (submit-feature-add))}
           "??????????????? localStorage"])))))