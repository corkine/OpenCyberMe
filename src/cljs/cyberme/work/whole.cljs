(ns cyberme.work.whole
  (:require [re-frame.core :as rf]
            [cyberme.work.core :refer [re-sharp-data hcm-calendar]]
            [cljs-time.core :as t]
            [goog.string :as gstring]
            [cuerdas.core :as str]))

(defn main-page []
  (let [{all-data :data} @(rf/subscribe [:hcm/all-data])
        {:keys [WeekWorkHour MonthWorkHour
                AllWorkHour AvgDayWorkHour AvgWeekWorkHour]} @(rf/subscribe [:hcm/hint-data])
        now (t/now)
        y-now (t/year now)
        m-now (t/month now)
        grouped-month (reduce (fn [acc kw-day]
                                (update acc (-> kw-day name (str/slice 0 7) keyword)
                                        #(conj % kw-day)))
                              {} (reverse (sort (keys all-data))))
        months (reverse (sort (keys grouped-month)))]
    [:div.is-flex.is-flex-wrap-wrap
     (for [month months]
       ^{:key month}
       [:div.block.ml-6.mr-6.mt-6
        (let [month-days (get grouped-month month)
              month-data (select-keys all-data month-days)
              [_ y m] (re-find #"(\d+)-(\d+)-(\d+)" (name (first month-days)))
              y (js/parseInt y)
              m (js/parseInt m)
              fake-now (t/last-day-of-the-month y m)]
          [:<>
           [:p.subtitle [:span "\uD83D\uDCC5 " y " / " m " "]
            (when (and (= y-now y) (= m-now m))
              [:span.has-text-grey-light.is-size-7
               (gstring/format "合计 %.1fh, %.1fh/月，%.1fh/天" AllWorkHour MonthWorkHour AvgDayWorkHour)])]
           [hcm-calendar fake-now false month-data]])])]))