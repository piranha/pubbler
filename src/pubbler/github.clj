(ns pubbler.github
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.core.strint :refer [<<]]
            [ring.util.codec :as codec]
            [org.httpkit.client :as http]
            [jsonista.core :as json]

            [pubbler.log :as log]
            [pubbler.bundle :as bundle]
            [pubbler.core.time :as time])
  (:import [java.security MessageDigest]))


(set! *warn-on-reflection* true)


(def ^:dynamic *user*)
(def sha1enc (MessageDigest/getInstance "SHA-1"))
(def BLOB (.getBytes "blob "))


;;; Utils


(defn join [& ks]
  (str/join "/"
    (map #(str/replace % #"^/*|/*$" "") ks)))


(defn stream->bytes [is]
  (let [baos (java.io.ByteArrayOutputStream.)]
    (io/copy is baos)
    (.toByteArray baos)))


(defn git-sha1 [ba]
  (let [ba     (if (string? ba)
                 (.getBytes ^String ba "UTF-8")
                 ba)
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
  (let [url  (str "https://api.github.com" url)
        res  (-req! method url {:body    (when data
                                           (json/write-value-as-string data))
                                :headers {"Accept"        "application/vnd.github.v3+json"
                                          "Authorization" (str "token " (:access_token *user*))}})
        data (-> res
                 :body
                 (json/read-value json/keyword-keys-object-mapper))]
    (if (:errors data)
      (throw (ex-info (:message data) {:data     data
                                       :response res}))
      (with-meta data {:response res}))))


;;; Logic

(defn update-file! [{:keys [tree path name content]}]
  (assert *user* "User context not set")
  (let [url-name (codec/url-encode name)
        glink    (<< "/repos/~(:github *user*)/~(:repo *user*)/contents/~{path}/~{url-name}")
        info     (first (filter #(= (:path %) name) tree))
        orig-sha (:sha info)]
    (log/info "file" {:target (join path name) :sha orig-sha})
    (when (or (nil? orig-sha)
              (not= orig-sha (git-sha1 content)))
      (req! :put glink {:message (<< "Updating ~{path}/~{name}")
                        :sha     orig-sha
                        :content content}))))


(defn upload-tb! [bundle]
  (when-not (:slug bundle)
    (throw (ex-info "Cannot guess path to upload your TextBundle"
             {:text (:text bundle)})))
  (log/info "upload" {:slug (:slug bundle)
                      :date (:date bundle)
                      :path (:path *user*)
                      :repo (:repo *user*)})
  (let [path (join
               (time/strftime (:path *user*) (:date bundle))
               (:slug bundle))
        tree (req! :get (<< "/repos/~(:github *user*)/~(:repo *user*)/git/trees/master:~(codec/url-encode path)") nil)]
    [path
     (concat
       (for [{:keys [link file]} (:files bundle)]
         [link (update-file! {:tree    (:tree tree)
                              :path    path
                              :name    link
                              :content (stream->bytes file)})])
       ;; `for` here so it'll be lazy
       (for [link ["index.md"]]
         [link (update-file! {:tree    (:tree tree)
                              :path    path
                              :name    link
                              :content (.getBytes ^String (:text bundle) "UTF-8")})]))]))



(comment
  (req! :put "/repos/piranha/beartest/contents/test"
    {:message "get back that stuff"
     :sha     "d41528e0d5185f7c83f24e9a0cb2e00ab1b91bb6"
     :content (.getBytes "just a test" "UTF-8")})

  (require 'pubbler.auth)
  (pubbler.auth/with-user (pubbler.auth/user-by-github "piranha")
    (req! :get "/repos/piranha/solovyov.net/git/trees/master:src%2Fblog%2F2021%2Ftest-note" nil #_
      {:message "another test"
       :content (stream->bytes (io/input-stream "/Users/piranha/q.jpg"))}))

  (req! :get "/repos/piranha/beartest/contents/test1" nil)

  (git-sha1 "just a test")

  (upload-tb!
    (bundle/read (io/input-stream (io/file "/Users/piranha/1/1.zip"))))
  )

