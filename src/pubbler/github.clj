(ns pubbler.github
  (:require [org.httpkit.client :as http]
            [jsonista.core :as json])
  (:import [java.util Base64]
           [java.security MessageDigest]))


(def BASE "https://api.github.com")

(def b64enc (-> (Base64/getEncoder)
                .withoutPadding))
(def sha1enc (MessageDigest/getInstance "SHA-1"))


(defn b64 [s]
  (.encode b64enc (.getBytes s "UTF-8")))


(defn git-sha1 [s]
  (let [target (str "blob " (count s) (char 0) s)
        enc    (MessageDigest/getInstance "SHA-1")
        digest (.digest enc (.getBytes target "UTF-8"))]
    (format "%040x" (BigInteger. 1 digest))))


(defn req [method url data]
  @(http/request
     {:url     (str BASE url)
      :method  method
      :headers {"Accept"        "application/vnd.github.v3+json"
                "Authorization" (str "token " (slurp "/Users/piranha/github-token"))}
      :body     (when data
                  (json/write-value-as-string data))}))



(comment
  (-> (req :put "/repos/piranha/beartest/contents/test"
        {:message "get back that stuff"
         :sha     "cafa86d5bc54d46867e2f66efee8b65c0d1a63c8"
         :content (b64 "just a test")})
      :body)

  (git-sha1 "just a test")


  )

