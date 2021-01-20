(ns pubbler.httpd
  (:require [clojure.string :as str]
            [mount.core :as mount]
            [org.httpkit.server :as httpd]
            [org.httpkit.client :as http]
            [jsonista.core :as json]

            [pubbler.bundle :as bundle]
            [pubbler.github :as github]))


(defn tg-token []
  (str/trim-newline (slurp "/Users/piranha/tg-token")))


(defn tg [method api-name params]
  (-> @(http/request
         {:url          (format "https://api.telegram.org/bot%s/%s"
                          (tg-token)
                          api-name)
          :method       method
          :query-params params})
      :body
      json/read-value))



(defn process-webhook [req]
  (let [data     (-> req :body json/read-value)
        file-id  (get-in data ["message" "document" "file_id"])
        filemeta (tg :get "getFile" {:file_id file-id})
        _        (prn filemeta)
        note     @(http/request
                    {:url (format "https://api.telegram.org/file/bot%s/%s"
                            (tg-token)
                            (get-in filemeta ["result" "file_path"]))})
        bundle   (bundle/read (:body note) "src/media/")]
    (github/upload-tb! bundle)
    {:status 200
     :body   "got it"}))


(defn app [req]
  (case (:uri req)
    "/telegram" (process-webhook req)
    {:status 404}))


(mount/defstate server
  :start (httpd/run-server app {:port 9000})
  :stop (server :timeout 100))

