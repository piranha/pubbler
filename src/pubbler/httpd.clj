(ns pubbler.httpd
  (:require [org.httpkit.server :as httpd]
            [mount.core :as mount]
            [ring.util.codec :as codec]
            [better-cond.core :as b]
            [clojure.walk :as walk]

            [pubbler.config :as config]
            [pubbler.auth :as auth]
            [pubbler.telegram :as telegram]))


;;; HTTPd

(defn gh-auth [{:keys [query-string]}]
  (b/cond
    :let [params (codec/form-decode query-string)
          code   (get params "code")
          state  (some-> (get params "state")
                   codec/base64-decode
                   (String. "UTF-8")
                   codec/form-decode
                   walk/keywordize-keys)]

    (nil? code)
    {:status 400
     :body   "No 'code' parameter in query string"}

    (nil? (:chat_id state))
    {:status 400
     :body   "Please restart process since I can't determine your chat id"}

    :let [user (auth/auth-user! code state)]

    (nil? user)
    {:status 400
     :body   "Cannot authenticate you on Github using supplied 'code'"}

    :done
    (do
      (telegram/reply (:chat_id user)
        (format "Hey %s, please tell me your target repo name" (:github user))
        {:forcereply true})
      {:status 200
       :body "Authenticated successfully! Please check your Telegram chat."})))


(defn app [req]
  (case (:uri req)
    "/oauth/github" (gh-auth req)
    {:status 404
     :body   "Not Found"}))


(mount/defstate server
  :start (httpd/run-server app {:port config/PORT})
  :stop (server))
