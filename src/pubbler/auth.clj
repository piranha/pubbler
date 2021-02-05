(ns pubbler.auth
  (:require [oauth.two :as oauth]
            [org.httpkit.client :as http]

            [pubbler.config :as config]
            [pubbler.db :as db]
            [pubbler.github :as github]
            [ring.util.codec :as codec]))


(defn get-base-url []
  (str "https://" (config/DOMAIN)))


(def gh-client
  (memoize
    (fn []
      (oauth/make-client
        {:authorize-uri "https://github.com/login/oauth/authorize"
         :access-uri    "https://github.com/login/oauth/access_token"
         :redirect-uri  (str (get-base-url) "/oauth/github")
         :scope         ["repo"]
         :id            (config/GHID)
         :secret        (config/GHSECRET)}))))


(defn oauth-url [state]
  (oauth/authorization-url (gh-client) {:state (when (seq state)
                                                 (-> state
                                                     codec/form-encode
                                                     (.getBytes "UTF-8")
                                                     codec/base64-encode))}))


(defn get-access-token! [client code]
  (-> @(http/request (oauth/access-token-request client {:code code}))
      :body
      slurp
      codec/form-decode
      (get "access_token")))


(defmacro with-user [user & body]
  `(binding [github/*user* ~user]
     ~@body))


(defn get-user-q [id]
  {:from   [:users]
   :select [:github
            :access_token
            :chat_id
            :telegram
            :repo
            :path
            :mapping]
   :where  [:= :github id]})


(defn store-user-q [info state]
  {:insert-into :users
   :values      [{:github       (:login info)
                  :access_token (:access_token state)
                  :chat_id      (:chat_id state)
                  :telegram     (:telegram state)
                  :updated_at   (db/call :now)}]
   :returning   (:select (get-user-q nil))
   :upsert      {:on-conflict   [:github]
                 :do-update-set [:access_token :chat_id :telegram :updated_at]}})


(defn store-user! [state]
  (let [info (with-user state
               (github/req! :get "/user" nil))]
    (db/one (store-user-q info state))))


(defn auth-user! [code state]
  (when-let [access-token (get-access-token! (gh-client) code)]
    (store-user! (assoc state :access_token access-token))))


(defn user-by-chat [id]
  (db/one (assoc (get-user-q nil)
            :where [:= :chat_id (str id)])))


(defn user-by-github [login]
  (db/one (get-user-q login)))
