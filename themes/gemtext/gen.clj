(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

(import '[java.nio.file Files])

(def headings
  '{header1 "# " header2 "## " header3 "### "
    header4 "### " header5 "### " header6 "### "})

(defn reference? [form]
  (and (seq? form) (#{'link 'image} (first form))))

(defn inline-text [form]
  (cond
    (string? form) form
    (vector? form) (apply str (map inline-text form))
    :else
    (let [[kind & children] form]
      (case kind
        image (second children)
        link (apply str (map inline-text (rest children)))
        (apply str (map inline-text children))))))

(defn page-target [meta page target]
  (let [[_ path suffix] (re-matches #"([^?#]*)(.*)" target)
        extension #"(?i)\.(?:html|md)$"]
    (if (and (re-find extension path)
             (not (re-find #"^(?:[a-zA-Z][a-zA-Z0-9+.-]*:|//)" path)))
      (let [root-relative? (str/starts-with? path "/")
            base (if root-relative? meta (.getParentFile page))
            relative (if root-relative? (subs path 1) path)]
        (if (.isFile (io/file base (str/replace relative extension ".clj")))
          (str (str/replace path extension ".gmi") suffix)
          target))
      target)))

(defn link-line [target-for [kind target :as form]]
  (let [label (str/trim (str/replace (inline-text form) #"\s+" " "))]
    (str "=> " (str/replace (if (= kind 'link) (target-for target) target) " " "%20")
         (when-not (str/blank? label) (str " " label)))))

(defn render-block [target-for [kind value :as block]]
  (let [text (inline-text block)
        references (filter reference? (tree-seq sequential? seq block))
        links (map #(link-line target-for %) references)
        body (case kind
               paragraph (when-not (every? #(or (reference? %)
                                                 (and (string? %) (str/blank? %)))
                                           value)
                           (if (re-find #"^(?:=>|#|\*\s|>|\x60{3})" text)
                             (str "\u200b" text)
                             text))
               blockquote (str "> " text)
               unordered-list (str/join "\n" (map #(str "* " (inline-text %)) (rest block)))
               ordered-list (str/join "\n" (map-indexed #(str (inc %1) ". " (inline-text %2))
                                                       (rest block)))
               code (let [fence (apply str (repeat 3 (char 96)))]
                      (str fence "\n" value "\n" fence))
               horizontal-rule "---"
               (if-let [heading (headings kind)]
                 (str heading text)
                 (throw (ex-info "Unknown intermediate form" {:form block}))))]
    (str/join "\n" (cond->> links (some? body) (cons body)))))

(defn generate! [meta-path out-path]
  (let [meta (io/file meta-path)
        template (io/file (.getParentFile (io/file *file*)) "templates" "index.gmi")
        mold (slurp template :encoding "UTF-8")]
    (doseq [file (file-seq meta)
            :when (.isFile file)]
      (let [path (str (.relativize (.toPath meta) (.toPath file)))
            page? (str/ends-with? path ".clj")
            dest (io/file out-path (if page? (str/replace path #"\.clj$" ".gmi") path))]
        (io/make-parents dest)
        (if page?
          (let [blocks (edn/read-string (slurp file :encoding "UTF-8"))
                target-for #(page-target meta file %)
                content (str/join "\n\n" (map #(render-block target-for %) blocks))]
            (spit dest (str/replace mold "{{CONTENT}}" content) :encoding "UTF-8"))
          (when-not (and (.isFile dest)
                         (= -1 (Files/mismatch (.toPath file) (.toPath dest))))
            (io/copy file dest)))))))

(apply generate! *command-line-args*)
