(ns cyberme.validation
  (:require [struct.core :as st]))

(def required
  {:message  "此字段不能为空"
   :optional false
   :validate #(if (string? %)
                (not (empty? %))
                (not (nil? %)))})

;{:name xx :message yy} -> {:name validate-hint :message validate-hint}
(def message-schema
  [#_[:name st/required st/string]
   [:message st/string {:message  "最少需要输入 10 个字符"
                        :validate (fn [msg] (>= (count msg) 10))}]])

(defn validate-message [params]
  (first (st/validate params message-schema)))

(def add-place-schema
  [#_[:id required {:message  "最少需要 4 个字符"
                  :validate (fn [msg] (>= (count msg) 4))}]
   [:place required st/string {:message  "最少需要 2 个字符"
                               :validate (fn [msg] (>= (count msg) 2))}]
   [:location required st/string {:message  "最少需要 2 个字符"
                                  :validate (fn [msg] (>= (count msg) 2))}]])

(def add-package-schema
  [[:name required st/string {:message  "最少需要 2 个字符，最多 15 个字符"
                              :validate (fn [msg] (and (>= (count msg) 2)
                                                       (<= (count msg) 15)))}]])

(defn validate-add-place [p]
  (first (st/validate p add-place-schema)))

(defn validate-edit-place [p]
  (first (st/validate p (conj add-place-schema [:id required]))))

(defn validate-add-package [p]
  (first (st/validate p add-package-schema)))
