(ns cyberme.psych.core
  "生效运势、星座配对服务，基于 APISpace https://www.apispace.com/explore/service/ 提供服务
  大部分服务利用了文件缓存降低请求次数。"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [org.httpkit.client :as http]
            [cheshire.core :as json])
  (:import (java.nio.file Paths)
           (java.time LocalDate)
           (java.time.format DateTimeFormatter)))

(defn- gen-star-pair-file [token]
  (let [file-output (.toString (Paths/get (System/getProperty "user.dir") (into-array ["star.json"])))]
    (spit file-output
          (json/generate-string
            (map
              (fn [{:keys [boy girl]}]
                (let [req (http/request {:url          "https://eolink.o.apispace.com/xzpd/api/v1/xzw/zodiac_pairing"
                                         :headers      {"X-APISpace-Token"   token
                                                        "Authorization-Type" "apikey"}
                                         :query-params {"boy_horoscope"   boy
                                                        "girls_horoscope" girl}})
                      resp @req]
                  (let [data (-> resp :body (json/parse-string true) :data)]
                    (println "fetched " boy girl "->" data)
                    (Thread/sleep 700)
                    data)))
              (flatten (map (fn [item] (map (fn [item2] {:boy item :girl item2}) star-12))
                            ["白羊座" "金牛座" "双子座" "巨蟹座" "狮子座" "处女座" "天秤座" "天蝎座" "射手座" "摩羯座" "水瓶座" "双鱼座"]))))))
  (let [file (.toString (Paths/get (System/getProperty "user.dir") (into-array ["star.json"])))
        new-file (str/replace file "star.json" "star_map.json")]
    (spit new-file
          (json/generate-string
            (into {}
                  (mapv (fn [{:keys [girls_horoscope boy_horoscope] :as data}]
                          [(str "B" boy_horoscope "+G" girls_horoscope) data])
                        (json/parse-string (slurp file) true)))))))

(defn star-pair
  "星座匹配，输入 双鱼座 双鱼座 返回格式：
  {:girls_horoscope 女星座
   :boy_horoscope 男星座
   :results_rvw 一句话总结
   :paired_proportions 匹配度 50:40
   :precautions 说明
   :pairing_idx 匹配度 80
   :longevity_idx n
   :compassion_idx n
   :love_advc 恋爱建议}"
  [boy_horoscope girls_horoscope]
  (let [file (.toString (Paths/get (System/getProperty "user.dir") (into-array ["resources" "docs" "star_map.json"])))]
    (if (.exists (io/file file))
      (let [data (json/parse-string (slurp file) true)
            key-find (str "B" boy_horoscope "+G" girls_horoscope)]
        (get data key-find))
      (do
        (println "place gen star_map.json use gen-star-pair-file func")
        nil))))

(defn history-today
  "历史上的今天，返回格式
  [{:year string?
    :title string?
    :festival string?
    :link string?
    :type string?
    :desc string?
    :cover string? 1 or 空
    :recommend string? 1 or 空}]"
  [token]
  (let [today (.format (LocalDate/now)
                       (DateTimeFormatter/ofPattern "yyyyMMdd"))
        file (.toString (Paths/get (System/getProperty "user.dir")
                                   (into-array [(str "history_" today ".json")])))]
    (if (.exists (io/file file))
      (do
        (println "fetched from file...")
        (json/parse-string (slurp file) true))
      (let [req (http/request {:url          "https://eolink.o.apispace.com/historydaily/api/v1/forward/HistoryDaily"
                               :headers      {"X-APISpace-Token"   token
                                              "Authorization-Type" "apikey"}})
            resp @req]
        (let [data (-> resp :body (json/parse-string true) :data)]
          (spit file (json/generate-string data))
          data)))))
