(ns pubbler.bundle
  (:require [clojure.java.io :as io]
            [ring.util.codec :as codec]
            [clojure.string :as str]))

(def BEAR-DIV "\n- - - -\n")
(def GOSTATIC-DIV "\n----\n")


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


(defn parse-text [text]
  (let [title        (second (re-find #"^# (.*)" text))
        header-start (str/index-of text BEAR-DIV)
        header-end   (str/index-of text BEAR-DIV (inc header-start))
        header       (.substring text (+ header-start (count BEAR-DIV)) header-end)
        header       (str "title: " title "\n" header)]
    {:text (str header GOSTATIC-DIV
             (.substring
               text
               (+ header-end (count BEAR-DIV))))
     :slug (or (parse-var header "slug")
               (title->slug title))}))


(defn map-assets [text target]
  (str/replace text #"!\[([^\]]*)\]\(assets/" (str "![$1](" target)))


(defn read [path asset-path]
  (let [text  (slurp (io/file path "text.markdown"))
        links (re-seq #"!\[.*?\]\(([^)]+)\)" text)
        files (for [[_ link] links
                    :let     [fp (codec/percent-decode link)]]
                {:link (str/replace link "assets/" asset-path)
                 :file (io/file path fp)})
        bundle (parse-text text)]
    (-> bundle
        (update :text map-assets asset-path)
        (assoc :files files))))


(comment
  (-> (read "/Users/piranha/qqq.textbundle/" "src/media/"))

  (title->slug "Test slug")
  (title->slug "Are we, - going to care!? about that?"))
