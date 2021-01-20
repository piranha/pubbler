(ns pubbler.github
  (:require [org.httpkit.client :as http]
            [jsonista.core :as json]

            [pubbler.bundle :as bundle]
            [clojure.java.io :as io]
            [com.brunobonacci.mulog :as u]
            [ring.util.codec :as codec])
  (:import [java.security MessageDigest]))


(def sha1enc (MessageDigest/getInstance "SHA-1"))


(def BLOB (.getBytes "blob "))


(defn stream->bytes [is]
  (loop [b (.read is) accum []]
    (if (< b 0)
      accum
      (recur (.read is) (conj accum b)))))


(defn git-sha1 [ba]
  (let [ba     (if (string? ba) (.getBytes ba) ba)
        target (byte-array (concat
                             BLOB
                             (.getBytes (str (count ba)))
                             [0]
                             ba))
        enc    (MessageDigest/getInstance "SHA-1")
        digest (.digest enc target)]
    (format "%040x" (BigInteger. 1 digest))))


(defn req [method url data]
  @(http/request
     {:url     (str "https://api.github.com" url)
      :method  method
      :headers {"Accept"        "application/vnd.github.v3+json"
                "Authorization" (str "token " (slurp "/Users/piranha/github-token"))}
      :body     (when data
                  (json/write-value-as-string data))}))


(defn update-file! [target content]
  (let [glink    (str "/repos/piranha/beartest/contents/" target)
        res      (req :get glink nil)
        orig-sha (-> res :body json/read-value (get "sha"))]
    (u/log ::upload
      :target target :status (:status res) :sha orig-sha)
    (when (or (= 404 (:status res))
              (not= orig-sha (git-sha1 content)))
      (req :put glink {:message (str "Updating " (codec/percent-decode target))
                       :sha     orig-sha
                       :content content}))))


(defn upload-tb! [bundle]
  (when-not (:slug bundle)
    (throw (ex-info "Cannot guess path to upload your TextBundle"
             {:text (:text bundle)})))
  (doseq [{:keys [link file]} (:files bundle)]
    (update-file! link (stream->bytes file)))
  (update-file!
    (str (:slug bundle)  ".md")
    (.getBytes (:text bundle) "UTF-8")))



(comment
  (-> (req :put "/repos/piranha/beartest/contents/test"
        {:message "get back that stuff"
         :sha     "d41528e0d5185f7c83f24e9a0cb2e00ab1b91bb6"
         :content (.getBytes "just a test" "UTF-8")})
      :body
      json/read-value)

  (-> (req :get "/repos/piranha/beartest/contents/test1" nil)
      )

  (git-sha1 "just a test")

  (upload-tb!
    (bundle/read (io/input-stream (io/file "/Users/piranha/1/1.zip")) "src/media/"))


  )

