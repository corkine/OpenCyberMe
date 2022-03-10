(ns cyberme.config
  (:require
    [cprop.core :refer [load-config]]
    [cprop.source :as source]
    [mount.core :refer [args defstate]]))

(defstate env
  :start
  (load-config
    :merge
    [(args)
     (source/from-system-props)
     (source/from-env)]))

(defn edn
  ([key]
   (get env key))
  ([key not-found]
   (get env key not-found)))

(defn edn-in
  ([key-vec]
   (get-in env key-vec))
  ([key-vec not-found]
   (get-in env key-vec not-found)))