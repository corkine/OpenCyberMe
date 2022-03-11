(ns cyberme.cyber.clean
  (:require [cyberme.db.core :as db]
            [clojure.tools.logging :as log])
  (:import (java.time LocalDate LocalTime)))

(defn today-info [{:keys [MorningBrushTeeth NightBrushTeeth
                          MorningCleanFace NightCleanFace]}]
  (let [now (LocalTime/now)
        night? (> (.getHour now) 20)]
    (if night?
      (cond (and MorningCleanFace MorningBrushTeeth
                 NightCleanFace NightBrushTeeth) "+1!"
            (and MorningCleanFace MorningBrushTeeth) "+1?"
            (and NightBrushTeeth NightCleanFace) "-1!"
            :else "-1!!")
      (cond (and MorningCleanFace MorningBrushTeeth) "+1?"
            :else "-1?"))))

(defn clean-count [this-year]
  (let [today (LocalDate/now)
        year-first (.minusYears today 1)
        this-year-map (reduce #(assoc % (:day %2) %2) {} this-year)
        ;;数据库返回的数据可能缺少日期，如果缺少则将其补齐，不包括今天
        year-dates (take-while #(.isAfter % year-first) (iterate #(.minusDays % 1) today))
        full-day-map (reduce (fn [acc date]
                               (assoc acc date (get this-year-map date {:day date :info {}})))
                             {} year-dates)
        without-today-map (dissoc full-day-map today)
        without-today-data (vals without-today-map)
        filter-fn (fn [{:keys [info]}]
                    (let [{:keys [MorningBrushTeeth NightBrushTeeth
                                  MorningCleanFace NightCleanFace]} info]
                      (true? (and MorningBrushTeeth NightBrushTeeth
                                  MorningCleanFace NightCleanFace))))
        keep-in (count (filter filter-fn without-today-data))]
    keep-in))

(defn handle-clean-show [{:keys []}]
  (try
    (let [today (LocalDate/now)
          year-first (.minusYears today 1)
          this-year (db/day-range {:from year-first :to today})
          today-db-data (if (.isEqual today (:day (first this-year))) (first this-year) nil)
          {:keys [MorningBrushTeeth NightCleanFace MorningCleanFace NightBrushTeeth] :as today-data}
          (:info today-db-data)
          keep-in (clean-count this-year)
          hint-suffix (today-info today-data)
          full-hint (str "\uD83C\uDF80" keep-in hint-suffix)]
      {:MorningBrushTeeth  (boolean MorningBrushTeeth)
       :NightBrushTeeth    (boolean NightBrushTeeth)
       :MorningCleanFace   (boolean MorningCleanFace)
       :NightCleanFace     (boolean NightCleanFace)
       :HabitCountUntilNow keep-in
       :HabitHint          full-hint                        ;🎀13-1?? 🎀13-1? 🎀13+1? 🎀13+1!
       })
    (catch Exception e
      (log/info "[clean-today] error: " (.getMessage e))
      {:MorningBrushTeeth  false
       :NightBrushTeeth    false
       :MorningCleanFace   false
       :NightCleanFace     false
       :HabitCountUntilNow 0
       :HabitHint          "\uD83C\uDF80?+1!"               ;🎀13-1?? 🎀13-1? 🎀13+1? 🎀13+1!
       })))

(defn handle-clean-update [{:keys [merge mt nt mf nf]
                            :or   {merge true mt false nt false mf false nf false}}]
  (let [{:keys [info]} (db/today)
        {:keys [MorningBrushTeeth NightCleanFace MorningCleanFace NightBrushTeeth]} info
        empty-info? (nil? info)
        full-data (if merge
                    {:MorningBrushTeeth (or MorningBrushTeeth (boolean mt))
                     :NightBrushTeeth   (or NightBrushTeeth (boolean nt))
                     :MorningCleanFace  (or MorningCleanFace (boolean mf))
                     :NightCleanFace    (or NightCleanFace (boolean nf))}
                    {:MorningBrushTeeth (boolean mt)
                     :NightBrushTeeth   (boolean nt)
                     :MorningCleanFace  (boolean mf)
                     :NightCleanFace    (boolean nf)})
        _ (println "origin data: " info ", new data: " full-data)
        changed? (not= {:MorningBrushTeeth MorningBrushTeeth
                        :NightBrushTeeth   NightBrushTeeth
                        :MorningCleanFace  MorningCleanFace
                        :NightCleanFace    NightCleanFace} full-data)
        _ (db/set-today {:info full-data})]
    {:message "今日数据已更新。"
     :code    500
     :update (or empty-info? changed?)}))

(comment
  (db/today)
  (db/set-today {:info {:A "B"}})
  (db/set-someday {:day (LocalDate/of 2022 04 01) :info {:A "B"}})
  (db/delete-day {:day (LocalDate/of 2022 04 01)})
  (db/day-range {:from (LocalDate/of 2021 01 01)
                 :to   (LocalDate/of 2022 04 02)})
  )