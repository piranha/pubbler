(ns pubbler.github
  (:require [clojure.java.io :as io]
            [com.brunobonacci.mulog :as u]
            [ring.util.codec :as codec]
            [org.httpkit.client :as http]
            [jsonista.core :as json]

            [pubbler.bundle :as bundle])
  (:import [java.security MessageDigest]))


(def ^:dynamic *user*)
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


(defn -req!
  "Inner function to make API calls in case you need more than just parsed
  response (i.e. status code etc)"
  [method url opts]
  (let [{:keys [error] :as res} @(http/request
                                   (assoc opts
                                     :url    url
                                     :method method))]
    (if error
      (throw (ex-info "http error" {:response res} error))
      res)))


(defn req! [method url data]
  (assert *user* "User context not set")
  (let [url (str "https://api.github.com" url)
        res (-req! method url {:body    (when data
                                          (json/write-value-as-string data))
                               :headers {"Accept"        "application/vnd.github.v3+json"
                                         "Authorization" (str "token " (:access_token *user*))}})]
    (with-meta
      (-> res
          :body
          (json/read-value json/keyword-keys-object-mapper))
      {:response res})))


(defn update-file! [target content]
  (let [glink    (str "/repos/piranha/beartest/contents/" target)
        info     (req! :get glink nil)
        status   (-> info meta :status)
        orig-sha (:sha info)]
    (u/log ::upload :target target :status status :sha orig-sha)
    (when (or (= status 404)
              (not= orig-sha (git-sha1 content)))
      (req! :put glink {:message (str "Updating " (codec/percent-decode target))
                        :sha     orig-sha
                        :content content}))))


(defn upload-tb! [bundle]
  (when-not (:slug bundle)
    (throw (ex-info "Cannot guess path to upload your TextBundle"
             {:text (:text bundle)})))
  (let [updates     (for [{:keys [link file]} (:files bundle)]
                      (update-file! link (stream->bytes file)))
        text-update (update-file!
                      (str (:slug bundle)  ".md")
                      (.getBytes (:text bundle) "UTF-8"))]
    (->> (conj (vec updates) text-update)
         (filterv identity))))



(comment
  (req! :put "/repos/piranha/beartest/contents/test"
    {:message "get back that stuff"
     :sha     "d41528e0d5185f7c83f24e9a0cb2e00ab1b91bb6"
     :content (.getBytes "just a test" "UTF-8")})

  (req! :get "/repos/piranha/beartest/contents/test1" nil)

  (git-sha1 "just a test")

  (upload-tb!
    (bundle/read (io/input-stream (io/file "/Users/piranha/1/1.zip")) "src/media/"))
  )

