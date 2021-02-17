(ns pubbler.httpd
  (:require [better-cond.core :as b]
            [mount.core :as mount]
            [org.httpkit.server :as httpd]
            [ring.util.codec :as codec]

            [pubbler.config :as config]
            [pubbler.auth :as auth]
            [pubbler.telegram :as telegram]))


(set! *warn-on-reflection* true)


;;; HTTPd

(defn gh-auth [{:keys [query-string]}]
  (b/cond
    :let [params (codec/form-decode query-string)
          code   (get params "code")
          state  (auth/b64->map (get params "state"))]

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
      (telegram/start-setup {:chat {:id (:chat_id user)}})
      {:status 200
       :body "Authenticated successfully! Please check your Telegram chat."})))


(defn app [req]
  (case (:uri req)
    "/oauth/github" (gh-auth req)
    {:status 404
     :body   "Not Found"}))


(mount/defstate server
  :start (httpd/run-server app {:port (config/PORT)})
  :stop (server))
