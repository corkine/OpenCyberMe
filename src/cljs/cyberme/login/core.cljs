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
    (let [{username :user password :pass} @(rf/subscribe [:api-auth])
          _ (when username (swap! fields assoc :username username))
          ;因为实际使用密文，因此这里直接显示为空
          ;_ (when password (swap! fields assoc :password password))
          ]
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
                  (cond (str/blank? username) (swap! errors assoc :username "用户名不应该为空")
                        (str/blank? password) (swap! errors assoc :password "密码不应该为空")
                        :else (do
                                (reset! fields {})
                                (reset! errors {})
                                (storage/set-item "api_auth"
                                                  {:username  username
                                                   :password
                                                   (tool/pass-encode
                                                     password
                                                     (case session
                                                           "5分钟" (* 60 5)
                                                           "1小时" (* 60 60)
                                                           "1天" (* 60 60 24)
                                                           "1周" (* 60 60 24 7)
                                                           "1月" (* 60 60 24 30)
                                                           "1年" (* 60 60 24 365)
                                                           (* 60 5)))
                                                   :update_at (str (js/Date.))})
                                (rf/dispatch [:app/hide-modal :login-info-set])
                                (rf/dispatch [:user/fetch-from-local])
                                (.reload js/location)))))]
        (modals/modal-button
          :login-info-set
          {:button    {:class ["is-info"]}
           :fix-title "登录"}
          [:i.fa.fa-user {:style {:margin-right :-10px}}]
          (if username "修改登录凭证" "登录")
          [:div {:style {:color "black"}}
           [common-fields :username "用户名" "用户名和密码信息仅保留在浏览器本地，用于 API 鉴权"]
           [common-fields :password "密码" "点击确定并不验证密码正确性，鉴权失败会弹出浏览器对话框"
            {:type :password}]
           [common-fields :session "登录时长" "登录凭证的有效期限"
            {:type :select :selects ["5分钟" "1小时" "1天" "1周" "1月" "1年"]}]]
          [:button.button.is-primary.is-fullwidth
           {:on-click (fn [_] (submit-feature-add))}
           "保存信息到 localStorage"])))))