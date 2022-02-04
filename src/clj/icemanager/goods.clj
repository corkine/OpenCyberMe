(ns icemanager.goods
  (:require
    [icemanager.db.core :as db]))

(defn all-place-with-goods []
  "将 SQL 返回的行列结果进行按位置聚合，将数据库字段转换为大小写驼峰样式，全部返回"
  (let [data (db/all-place)
        grouped-data (group-by :placeid data)
        map-data
        (map #(let [place-goods (val %)
                    {:keys [placeid place location description]} (first place-goods)
                    no-place-goods
                    (map
                      (fn [good]
                        (let [fix-camel (assoc good
                                          :createAt (:createat good)
                                          :updateAt (:updateat good))
                              no-place (dissoc fix-camel
                                               :placeid :place
                                               :location :description
                                               :createat :updateat)] no-place))
                      place-goods)
                    filtered-goods
                    (filter (fn [g] (not (nil? (:id g)))) no-place-goods)]
                {:id          placeid
                 :place       place
                 :location    location
                 :description description
                 :items       filtered-goods})
             grouped-data)]
    (vec map-data)))

