(ns cyberme.cyber.clean
  (:require [cyberme.db.core :as db]
            [clojure.tools.logging :as log])
  (:import (java.time LocalDate LocalTime LocalDateTime)))

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
        passed-dates (take-while #(.isAfter % year-first) (iterate #(.minusDays % 1) (.minusDays today 1)))
        is-ok-day? (fn [{:keys [info]}]
                     (let [{:keys [MorningBrushTeeth NightBrushTeeth
                                   MorningCleanFace NightCleanFace]} info]
                       (true? (and MorningBrushTeeth NightBrushTeeth
                                   MorningCleanFace NightCleanFace))))
        find-fn #(is-ok-day? (get this-year-map %))
        keep-in (count (take-while find-fn passed-dates))]
    keep-in))

(defn non-blue-count [this-year]
  (let [today (LocalDate/now)
        year-first (.minusYears today 1)
        this-year-map (reduce #(assoc % (:day %2) %2) {} this-year)
        ;;数据库返回的数据可能缺少日期，如果缺少则将其补齐，不包括今天
        passed-dates (take-while #(.isAfter % year-first) (iterate #(.minusDays % 1) (.minusDays today 1)))
        blue-fn #(-> % :info :blue boolean)
        find-fn #(let [day-info (get this-year-map %)] (or (nil? day-info) (not (blue-fn day-info))))
        keep-in (count (take-while find-fn passed-dates))
        today? (-> (get this-year-map today) :info :blue boolean)]
    {:max-count keep-in :today today?}))

(defn handle-blue-week
  "获取本周的 blue 数据：{:2022-03-01 true :2022-03-02 false..}"
  []
  (let [today (LocalDate/now)
        today-day-of-week (.getValue (.getDayOfWeek today))
        week-first (.minusDays today (- today-day-of-week 1))
        all-week-day (take 7 (iterate #(.plusDays % 1) week-first))
        ;type: [{:day date :info {:blue t/f} :create_at :update_at ts}]
        data-in-db (db/day-range {:from week-first :to today})
        ;type: {:2022-03-01 {:day 2022-03-01 :info :create_at :update_at}
        db-data-map (reduce #(assoc % (:day %2) %2) {} data-in-db)]
    (reduce #(assoc %
               (-> %2 str keyword)
               (boolean (-> (get db-data-map %2) :info :blue)))
            {} all-week-day)))

(defn handle-score-week
  "获取本周的 score 数据：{:2022-03-01 33 :2022-03-02 79..}"
  []
  (let [today (LocalDate/now)
        today-day-of-week (.getValue (.getDayOfWeek today))
        week-first (.minusDays today (- today-day-of-week 1))
        all-week-day (take 7 (iterate #(.plusDays % 1) week-first))
        ;type: [{:day date :info {:blue t/f} :create_at :update_at ts}]
        data-in-db (db/day-range {:from week-first :to today})
        ;type: {:2022-03-01 {:day 2022-03-01 :info :create_at :update_at}
        db-data-map (reduce #(assoc % (:day %2) %2) {} data-in-db)]
    (reduce #(assoc %
               (-> %2 str keyword)
               (or (-> (get db-data-map %2) :info :score) 0))
            {} all-week-day)))

(defn handle-blue-show []
  (try
    (let [today (LocalDate/now)
          month-first (LocalDate/of (.getYear today) (.getMonth today) 1)
          week-first (.minusDays today (- (.getValue (.getDayOfWeek today)) 1))
          year-first (.minusYears today 1)
          this-year (db/day-range {:from year-first :to today})
          this-month (db/day-range {:from month-first :to today})
          this-week (db/day-range {:from week-first :to today})
          blue-month (count (filter #(-> % :info :blue boolean) this-month))
          blue-week (count (filter #(-> % :info :blue boolean) this-week))
          {:keys [max-count today]} (non-blue-count this-year)]
      {:UpdateTime           (LocalDateTime/now)
       :IsTodayBlue          today
       :WeekBlueCount        blue-week
       :MonthBlueCount       blue-month
       :MaxNoBlueDay         max-count
       :MaxNoBlueDayFirstDay (.minusDays (LocalDate/now) max-count)})
    (catch Exception e
      (do
        (log/info "[handle-blue] error: " (.getMessage e))
        {:UpdateTime          (LocalDateTime/now)
         :IsTodayBlue         false
         :WeekBlueCount       -1
         :MonthBlueCount      -1
         :MaxNoBlueDay        -1
         :MaxNoBlueDayLastDay (LocalDateTime/now)}))))

(defn handle-clean-week
  "获取本周的 clean 数据：{:2022-03-01 {:mf :mb xx} :2022-03-02..}"
  []
  (let [today (LocalDate/now)
        today-day-of-week (.getValue (.getDayOfWeek today))
        week-first (.minusDays today (- today-day-of-week 1))
        all-week-day (take 7 (iterate #(.plusDays % 1) week-first))
        ;type: [{:day date :info {:blue t/f} :create_at :update_at ts}]
        data-in-db (db/day-range {:from week-first :to today})
        ;type: {:2022-03-01 {:day 2022-03-01 :info :create_at :update_at}
        db-data-map (reduce #(assoc % (:day %2) %2) {} data-in-db)]
    (reduce #(assoc %
               (-> %2 str keyword)
               (let [{:keys [MorningBrushTeeth NightCleanFace MorningCleanFace NightBrushTeeth]}
                     (-> (get db-data-map %2) :info)]
                 {:MorningBrushTeeth (boolean MorningBrushTeeth)
                  :NightBrushTeeth   (boolean NightBrushTeeth)
                  :MorningCleanFace  (boolean MorningCleanFace)
                  :NightCleanFace    (boolean NightCleanFace)}))
            {} all-week-day)))

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
          full-hint (str "" keep-in hint-suffix)]
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
       :HabitHint          "?+1!"                           ;🎀13-1?? 🎀13-1? 🎀13+1? 🎀13+1!
       })))

(defn handle-clean-update [{:keys [merge mt nt mf nf yesterday]
                            :or   {merge true mt false nt false mf false nf false yesterday false}}]
  (let [{:keys [info]} (if yesterday (db/someday {:day (.minusDays (LocalDate/now) 1)})
                                     (db/today))
        {:keys [MorningBrushTeeth NightCleanFace MorningCleanFace NightBrushTeeth]} info
        full-data (if merge
                    {:MorningBrushTeeth (or MorningBrushTeeth (boolean mt))
                     :NightBrushTeeth   (or NightBrushTeeth (boolean nt))
                     :MorningCleanFace  (or MorningCleanFace (boolean mf))
                     :NightCleanFace    (or NightCleanFace (boolean nf))}
                    {:MorningBrushTeeth (boolean mt)
                     :NightBrushTeeth   (boolean nt)
                     :MorningCleanFace  (boolean mf)
                     :NightCleanFace    (boolean nf)})
        changed? (not= {:MorningBrushTeeth MorningBrushTeeth
                        :NightBrushTeeth   NightBrushTeeth
                        :MorningCleanFace  MorningCleanFace
                        :NightCleanFace    NightCleanFace} full-data)
        _ (if yesterday
            (db/set-someday {:day  (.minusDays (LocalDate/now) 1)
                             :info (clojure.core/merge (or info {}) full-data)})
            (db/set-today {:info (clojure.core/merge (or info {}) full-data)}))]
    {:message (if yesterday "昨日数据已更新。" "今日数据已更新。")
     :code    500
     :update  changed?}))

(defn handle-blue-set [{:keys [blue day]}]
  (try
    (let [day (if (nil? day)
                (LocalDate/now)
                (try
                  (LocalDate/parse day)
                  (catch Exception _ (LocalDate/now))))
          {:keys [info]} (db/someday {:day day})
          old-blue (:blue info)
          _ (db/set-someday {:day  day
                             :info (assoc (or info {}) :blue blue)})]
      {:message (str "设置 Blue：" blue " 成功。")
       :status  1
       :update  (if (= old-blue blue) false true)})
    (catch Exception e
      (log/error "[blue-set] error: " (.getMessage e))
      {:message (str "设置 Blue：" blue " 失败。")
       :status  0
       :update  false})))

(comment
  (db/today)
  (db/someday {:day (LocalDate/now)})
  (db/set-today {:info {:A "B"}})
  (db/set-someday {:day (LocalDate/of 2022 04 01) :info {:A "B"}})
  (db/delete-day {:day (LocalDate/of 2022 04 01)})
  (db/day-range {:from (LocalDate/of 2021 01 01)
                 :to   (LocalDate/of 2022 04 02)})
  )