(ns cyberme.util.storage)

(defn set-item [key value]
  (.setItem js/localStorage key (.stringify js/JSON (clj->js value))))

(defn get-item [key]
  (try
    (js->clj (.parse js/JSON (.getItem js/localStorage key)) :keywordize-keys true)
    (catch js/Error _ nil)))