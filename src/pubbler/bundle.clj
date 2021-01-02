(ns pubbler.bundle
  (:require [clojure.java.io :as io]
            [ring.util.codec :as codec]
            [clojure.string :as str]))

(def BEAR-DIV "\n- - - -\n")
(def GOSTATIC-DIV "\n----\n")


(defn fix-text [text]
  (let [title        (second (re-find #"^# (.*)" text))
        header-start (str/index-of text BEAR-DIV)
        header-end   (str/index-of text BEAR-DIV (inc header-start))
        header       (.substring text (+ header-start (count BEAR-DIV)) header-end)
        header (str "title: " title "\n" header)]
    (str header GOSTATIC-DIV
      (.substring
        text
        (+ header-end (count BEAR-DIV))))))


(defn read [path]
  (let [text  (slurp (io/file path "text.markdown"))
        links (re-seq #"!\[.*?\]\(([^)]+)\)" text)
        files (for [[_ link] links]
                (io/file path (codec/percent-decode link)))]
    {:text  (fix-text text)
     :files files}))


(comment
  (-> (read "/Users/piranha/qqq.textbundle/")
      :files
      first
      .exists))
