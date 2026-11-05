(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

(import '[java.nio.file Files])

(def tags
  '{header1 "h1" header2 "h2" header3 "h3"
    header4 "h4" header5 "h5" header6 "h6"
    paragraph "p" blockquote "blockquote" unordered-list "ul"
    ordered-list "ol" list-item "li" bold "strong" italic "em"})

(defn escape-html [s]
  (str/escape (str s) {\& "&amp;" \< "&lt;" \> "&gt;"
                        \" "&quot;" \' "&#39;"}))

(defn render [form]
  (cond
    (string? form) (escape-html form)
    (vector? form) (apply str (map render form))
    :else
    (let [[kind & children] form
          [value label] children]
      (case kind
        horizontal-rule "<hr>"
        code (str "<pre><code>" (escape-html value) "</code></pre>")
        image (str "<img src=\"" (escape-html value)
                   "\" alt=\"" (escape-html label) "\">")
        link (str "<a href=\"" (escape-html value) "\">"
                  (apply str (map render (rest children))) "</a>")
        (if-let [tag (tags kind)]
          (str "<" tag ">" (apply str (map render children)) "</" tag ">")
          (throw (ex-info "Unknown intermediate form" {:form form})))))))

(defn gallery-images [block]
  (when (= 'paragraph (first block))
    (let [images (remove #(and (string? %) (str/blank? %)) (second block))]
      (when (and (seq images)
                 (every? #(and (seq? %) (= 'image (first %))) images))
        images))))

(defn render-content [blocks]
  (str/join "\n"
            (map (fn [group]
                   (if (gallery-images (first group))
                     (str "<div class=\"gallery\">\n"
                          (str/join "\n"
                                    (for [[_ src alt :as image] (mapcat gallery-images group)]
                                      (str "<a href=\"" (escape-html src)
                                           "\" aria-label=\"" (escape-html
                                             (if (str/blank? alt) "Open image" (str "Open " alt)))
                                           "\">" (render image) "</a>")))
                          "\n</div>")
                     (str/join "\n" (map render group))))
                 (partition-by #(boolean (gallery-images %)) blocks))))

(defn title-text [form]
  (if (string? form)
    form
    (let [[kind & children] form]
      (case kind
        image (second children)
        link (apply str (map title-text (rest children)))
        (apply str (map title-text children))))))

(defn relative-path [root file]
  (.relativize (.toPath root) (.toPath file)))

(defn copy-file! [source dest]
  ;; Windows can reject writes to assets that a viewer has mapped.
  (when-not (and (.isFile dest)
                 (= -1 (Files/mismatch (.toPath source) (.toPath dest))))
    (io/make-parents dest)
    (io/copy source dest)))

(defn generate! [meta-path out-path]
  (let [meta-dir (io/file meta-path)
        template-dir (io/file (.getParentFile (io/file *file*)) "templates" "html")
        template (io/file template-dir "index.html")
        mold (slurp template :encoding "UTF-8")]
    (doseq [file (file-seq template-dir)
            :when (and (.isFile file) (not= file template))]
      (copy-file! file (io/file out-path (str (relative-path template-dir file)))))
    (doseq [file (file-seq meta-dir)
            :when (.isFile file)]
      (let [relative (relative-path meta-dir file)
            path (str relative)]
        (if (str/ends-with? path ".clj")
          (let [blocks (edn/read-string (slurp file :encoding "UTF-8"))
                heading (some #(when (= 'header1 (first %)) %) blocks)
                title (if heading
                        (title-text heading)
                        (str/replace (.getName file) #"\.clj$" ""))
                content (render-content blocks)
                root (apply str (repeat (dec (.getNameCount relative)) "../"))
                dest (io/file out-path (str/replace path #"\.clj$" ".html"))
                values {"TITLE" (escape-html title) "CONTENT" content
                        "BODY" content "ROOT" root}]
            (io/make-parents dest)
            (spit dest (str/replace mold #"\{\{(TITLE|CONTENT|BODY|ROOT)\}\}"
                                    (fn [[_ key]] (values key)))
                  :encoding "UTF-8"))
          (copy-file! file (io/file out-path path)))))))

(apply generate! *command-line-args*)
