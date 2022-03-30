(ns cyberme.util.markdown
  (:require ["react-markdown" :default ReactMarkdown]
            ["remark-gfm" :default remarkGfm]
            #_["react-syntax-highlighter" :default SyntaxHighlighter]
            #_["react-syntax-highlighter/dist/esm/styles/hljs" :refer (a11yLight)]
            [re-frame.core :as rf]
            [clojure.string :as string]
            [reagent.core :as r]))

(defn mark-down [content-str]
  ;see https://github.com/remarkjs/remark-gfm
  ;style https://github.com/react-syntax-highlighter/react-syntax-highlighter/blob/master/AVAILABLE_STYLES_HLJS.MD
  [:> ReactMarkdown {:children      content-str
                     :linkTarget    "_blank"
                     :remarkPlugins [[(r/as-element remarkGfm) {:singleTilde true}]]
                     :components
                     (clj->js
                       {:img
                        (fn [args]
                          (let [{:keys [src alt]
                                 :as   all} (js->clj args :keywordize-keys true)]
                            (try
                              (let [alt (or alt "")
                                    with-hint? (and (string/includes? alt "::")
                                                    (string/includes? alt "*"))]
                                (if with-hint?
                                  (let [[_ alt w h] (re-find #"(\w+)?::(\w+)?\*(\w+)?" alt)
                                        alt (or alt "")]
                                    (r/as-element [:img (cond (and (nil? w) (nil? h))
                                                              {:src (str src) :alt alt}
                                                              (nil? w)
                                                              {:src (str src) :alt alt :height h}
                                                              (nil? h)
                                                              {:src (str src) :alt alt :width w}
                                                              :else
                                                              {:src (str src) :alt alt :height h :width w})]))
                                  (r/as-element [:img {:src (str src) :alt alt}])))
                              (catch js/Error e
                                (println "error when parsing markdown :alt info, you should set {desc}::23px*34px
                                to hint img width and height, eg ::*34px ::230px*：" e)
                                (r/as-element [:img {:src (str src)}])))))
                        #_:code
                        #_(fn [args]
                          (let [{:keys [node inline className children]
                                 :as   all} (js->clj args :keywordize-keys true)]
                            (if (string/includes? (or className "") "language-")
                              (r/as-element [:> SyntaxHighlighter
                                             {:children (string/join "\n" children)
                                              :language (string/replace className "language-" "")
                                              :PreTag   "div"
                                              :style    a11yLight}])
                              (r/as-element [:code {:className className
                                                    :children  children}]))))})}])