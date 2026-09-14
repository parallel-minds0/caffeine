(ns gen
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :as pp]))

(def meta-dir ".meta")
(def themes-dir "themes")

(defn die! [msg]
  (binding [*out* *err*] (println msg))
  (System/exit 1))

(defn slash [s]
  (str/replace (str s) #"\\" "/"))

(defn rel [root file]
  (slash
   (str
    (.relativize
     (.toPath (io/file root))
     (.toPath (io/file file))))))

(defn parse-args [args]
  (loop [xs args r {}]
    (if-not (seq xs)
      r
      (let [[a b & more] xs]
        (case a
          "--src"   (recur more (assoc r :src b))
          "--theme" (recur more (assoc r :theme b))
          "--out"   (recur more (assoc r :out b))
          (throw (ex-info (str "Unknown argument: " a) {})))))))

;; ---------------------------------------------------------------------
;; Theme selection
;; ---------------------------------------------------------------------

(defn themes []
  (->> (.listFiles (io/file themes-dir))
       (filter #(.isDirectory ^java.io.File %))
       (map #(.getName ^java.io.File %))
       sort
       vec))

(defn choose-theme [xs requested]
  (if requested
    (if (some #{requested} xs)
      requested
      (throw (ex-info (str "Theme not found: " requested) {})))
    (do
      (println "\nAvailable themes:")
      (doseq [[i x] (map-indexed vector xs)]
        (println (format "  %d. %s" (inc i) x)))
      (print "\nChoose a theme by number: ")
      (flush)
      (let [n (try
                (Integer/parseInt (str/trim (read-line)))
                (catch Exception _ 0))]
        (if (<= 1 n (count xs))
          (nth xs (dec n))
          (throw (ex-info "Invalid theme selection." {})))))))

;; ---------------------------------------------------------------------
;; Markdown parser
;; ---------------------------------------------------------------------

(defn external? [s]
  (or (str/blank? s)
      (re-find #"^(?:[A-Za-z][A-Za-z0-9+.-]*:|//|/|#|[A-Za-z]:[\\/])" s)))

(defn split-ref [s]
  (let [[p f] (str/split s #"#" 2)
        [p q] (str/split (or p "") #"\?" 2)]
    [p (if q (str "?" q) "") (if f (str "#" f) "")]))

(defn rewrite-ref [src file target]
  (if (external? target)
    target
    (let [[p q f] (split-ref target)
          source (.toPath (io/file file))
          root   (.toPath (io/file src))
          resolved (.normalize (.resolve (.getParent source) p))
          from-root (slash (str (.relativize root resolved)))
          output (str/replace from-root #"(?i)\.md$" ".html")
          page (str/replace (rel src file) #"(?i)\.md$" ".html")
          page-dir (.getParent (.toPath (io/file page)))
          result (if page-dir
                   (slash (str (.relativize page-dir
                                           (.toPath (io/file output)))))
                   output)]
      (str result q f))))

(declare inline)

(defn inline [src file s]
  (loop [i 0 buf "" out []]
    (if (>= i (count s))
      (cond-> out (seq buf) (conj buf))
      (let [c (nth s i)
            flush (cond-> out (seq buf) (conj buf))]
        (cond
          (and (= c \!)
               (< (inc i) (count s))
               (= \[ (nth s (inc i))))
          (let [a (+ i 2) b (str/index-of s "]" a)]
            (if (and b (< (inc b) (count s)) (= \( (nth s (inc b))))
              (let [d (str/index-of s ")" (+ b 2))]
                (if d
                  (recur (inc d) ""
                         (conj flush
                               (list 'image
                                     (rewrite-ref src file
                                                  (subs s (+ b 2) d))
                                     (subs s a b))))
                  (recur (inc i) (str buf c) out)))
              (recur (inc i) (str buf c) out)))

          (= c \[)
          (let [a (inc i) b (str/index-of s "]" a)]
            (if (and b (< (inc b) (count s)) (= \( (nth s (inc b))))
              (let [d (str/index-of s ")" (+ b 2))]
                (if d
                  (recur (inc d) ""
                         (conj flush
                               (apply list 'link
                                      (rewrite-ref src file
                                                   (subs s (+ b 2) d))
                                      (inline src file (subs s a b)))))
                  (recur (inc i) (str buf c) out)))
              (recur (inc i) (str buf c) out)))

          (or (= c \*) (= c \_))
          (let [two? (and (< (inc i) (count s))
                          (= c (nth s (inc i))))
                d (if two? (str c c) (str c))
                a (+ i (count d))
                b (str/index-of s d a)]
            (if (and b (> b a))
              (recur (+ b (count d)) ""
                     (conj flush
                           (apply list
                                  (if two? 'bold 'italic)
                                  (inline src file (subs s a b)))))
              (recur (inc i) (str buf c) out)))

          :else
          (recur (inc i) (str buf c) out))))))

(defn header [s]
  (re-matches #"(#{1,6})\s+(.*)" s))

(defn fence? [s]
  (str/starts-with? (str/trim s) "```"))

(defn hr? [s]
  (boolean (re-matches #"\s*(-{3,}|\*{3,}|_{3,})\s*" s)))

(defn ul [s]
  (re-matches #"\s*[-*+]\s+(.*)" s))

(defn ol [s]
  (re-matches #"\s*\d+\.\s+(.*)" s))

(defn bq [s]
  (re-matches #"\s*>\s?(.*)" s))

(defn block-start? [s]
  (or (str/blank? s) (header s) (fence? s) (hr? s)
      (ul s) (ol s) (bq s)))

(defn blocks [src file text]
  (loop [ls (str/split-lines text) out []]
    (if-not (seq ls)
      out
      (let [line (first ls)]
        (cond
          (str/blank? line)
          (recur (rest ls) out)

          (fence? line)
          (let [[code rest] (split-with #(not (fence? %)) (rest ls))]
            (when-not (seq rest)
              (throw (ex-info (str "Unterminated code fence in " file) {})))
            (recur (rest rest)
                   (conj out (list 'code (str/join "\n" code)))))

          (header line)
          (let [[_ marks text] (header line)]
            (recur (rest ls)
                   (conj out
                         (apply list
                                (nth [nil 'header1 'header2 'header3
                                      'header4 'header5 'header6]
                                     (count marks))
                                (inline src file (str/trim text))))))

          (hr? line)
          (recur (rest ls) (conj out '(horizontal-rule)))

          (bq line)
          (let [[xs rest] (split-with bq ls)]
            (recur rest
                   (conj out
                         (apply list 'blockquote
                                (inline src file
                                        (str/join " "
                                                  (map second xs)))))))

          (ul line)
          (let [[xs rest] (split-with ul ls)]
            (recur rest
                   (conj out
                         (apply list 'unordered-list
                                (map #(apply list 'list-item
                                              (inline src file (second (ul %))))
                                     xs)))))

          (ol line)
          (let [[xs rest] (split-with ol ls)]
            (recur rest
                   (conj out
                         (apply list 'ordered-list
                                (map #(apply list 'list-item
                                              (inline src file (second (ol %))))
                                     xs)))))

          :else
          (let [[xs rest]
                (split-with #(not (block-start? %)) ls)]
            (recur rest
                   (conj out
                         (list 'paragraph
                               (inline src file
                                       (str/join " " xs)))))))))))

;; ---------------------------------------------------------------------
;; Exact source-tree mirroring
;; ---------------------------------------------------------------------

(defn source-files [src]
  (->> (file-seq (io/file src))
       (filter #(.isFile ^java.io.File %))
       (sort-by #(rel src %))))

(defn generate-meta! [src]
  (let [files (source-files src)]
    (when-not (seq files)
      (throw (ex-info "Source directory contains no files." {})))

    (doseq [file files]
      (let [r (rel src file)
            md? (boolean (re-find #"(?i)\.md$" r))
            dest (io/file meta-dir
                          (if md?
                            (str/replace r #"(?i)\.md$" ".clj")
                            r))]
        (io/make-parents dest)

        (if md?
          (do
            (with-open [w (io/writer dest)]
              (binding [*out* w
                        *print-readably* true]
                (println (str ";; Generated from: " r))
                (println)
                (pp/pprint
                 (blocks src file (slurp file)))))
            ;; Validate immediately.
            (read-string (slurp dest))
            (println "Generated intermediate file:" (slash dest)))

          (do
            (io/copy file dest)
            (println "Copied source file:" (slash dest))))))))

;; ---------------------------------------------------------------------
;; Theme generator
;; ---------------------------------------------------------------------

(defn run-theme! [theme out]
  (let [gen (slash (io/file themes-dir theme "gen.clj"))
        p (-> (ProcessBuilder.
               ["clojure" "-M" gen meta-dir out])
              (.inheritIO)
              (.start))
        code (.waitFor p)]
    (when-not (zero? code)
      (throw
       (ex-info
        (str "Theme generator for '" theme
             "' exited with code " code)
        {})))))

(defn -main [& args]
  (try
    (let [{:keys [src theme out]} (parse-args args)
          src (or src "content")
          out (or out "out")
          ts (themes)
          selected (choose-theme ts theme)]

      (println "Discovered themes:" (str/join ", " ts))
      (println "Selected theme:" selected)

      (when-not (.isDirectory (io/file src))
        (throw (ex-info (str "Source directory not found: " src) {})))

      (io/make-parents (io/file meta-dir "placeholder"))
      (generate-meta! src)
      (println "Intermediate generation complete.")

      (run-theme! selected out)
      (println "Website generation complete."))

    (catch Exception e
      (die! (str "Generation failed: " (.getMessage e))))))

(apply -main *command-line-args*)
