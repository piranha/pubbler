(ns pubbler.bundle
  (:refer-clojure :exclude [read])
  (:import [java.util.zip ZipFile]
           [java.io File])
  (:require [clojure.java.io :as io]
            [ring.util.codec :as codec]
            [clojure.string :as str]

            [pubbler.core.time :as time]))


(def BEAR-DIV "\n---\n")


(defn parse-var [header var-name]
  (let [re (re-pattern (str "(?m)^" var-name ":(.*)$"))]
    (some-> (re-find re header)
      (second)
      (str/trim))))


(defn title->slug [title]
  (-> title
      str/lower-case
      (str/replace #"[^\w\d]+" "-")
      (str/trim)
      (str/replace #"^\-|\-$" "")))


(defn ensure-newline-eof [s]
  (cond-> s
    (not= \newline (.charAt s (dec (count s))))
    (str \newline)))


(defn parse-text [text]
  (let [title        (second (re-find #"^# (.*)" text))
        header-start (str/index-of text BEAR-DIV)
        header-end   (str/index-of text BEAR-DIV (inc header-start))
        header       (.substring text (+ header-start (count BEAR-DIV)) header-end)
        header       (str "title: " title "\n" header)
        text         (-> (str "---\n" header "\n---\n"
                           (.substring
                             text
                             (+ header-end (count BEAR-DIV))))
                         ensure-newline-eof)]
    {:text text
     :slug (or (parse-var header "slug")
               (title->slug title))
     :date (or (some-> (parse-var header "date")
                 time/parse)
               (time/today))}))


(defn fix-bear-images
  "This function fixes plain-text image references in Bear notes to be a
  markdown-style image, i.e. ![](img.jpg)."
  [text]
  (str/replace text #"(?m)^\[assets/(.*)\]$"
    (fn [[_ link]]
      (format "![](%s)" (codec/url-encode link)))))


(defn determine-prefix [zip-file]
  (let [entry (first (enumeration-seq (.entries zip-file)))
        i     (str/index-of (.getName entry) "/")]
    (.substring (.getName entry) 0 (inc i))))


(defn zip-entry [zip-file path]
  (let [entry    (.getEntry zip-file path)]
    (.getInputStream zip-file entry)))


(defn is->zip [input-stream]
  (let [f (doto (File/createTempFile "pubbler" ".zip" (File. "/tmp"))
            .deleteOnExit)]
    (io/copy input-stream f)
    (ZipFile. f)))


(defn read [input-stream]
  (let [zip-file (is->zip input-stream)
        prefix   (determine-prefix zip-file)
        text     (slurp (zip-entry zip-file (str prefix "text.txt")))
        ;; links (re-seq #"!\[.*?\]\(([^)]+)\)" text)
        links    (re-seq #"(?m)^\[(assets/.+)\]$" text)
        files    (for [[_ link] links
                       :let     [fp link]]
                   {:link (str/replace link "assets/" "")
                    :file (zip-entry zip-file (str prefix fp))})
        bundle   (parse-text text)]
    (-> bundle
        (update :text fix-bear-images)
        (assoc :files files))))


(comment
  (-> (read (io/input-stream (io/file "/Users/piranha/1/1.zip"))))

  (-> (io/file "/Users/piranha/1/1.zip")
      (ZipFile.)
      (determine-prefix))

  (title->slug "Test slug")
  (title->slug "Are we, - going to care!? about that?"))
