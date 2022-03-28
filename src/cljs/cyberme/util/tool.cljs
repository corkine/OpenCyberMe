(ns cyberme.util.tool
  (:require [clojure.string :as string]
            [cljs-time.core :as t]
            [cljs-time.format :as format]))

(defn l->s [vect]
  (string/join ", " (if (nil? vect) [] vect)))

(defn s->l [input]
  (let [input (or input "")
        input (string/replace input "，" ",")]
    (vec (filter (comp not string/blank?)
                 (map string/trim (string/split input ","))))))

(defn today-str []
  (format/unparse-local (format/formatter "yyyy-MM-dd")
                        (t/time-now)))