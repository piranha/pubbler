(ns pubbler.telegram
  (:require [clojure.string :as str]
            [ring.util.codec :as codec]
            [com.brunobonacci.mulog :as u]
            [org.httpkit.client :as http]
            [jsonista.core :as json]

            [pubbler.config :as config]
            [pubbler.bundle :as bundle]
            [pubbler.github :as github]
            [pubbler.auth :as auth]
            [pubbler.db :as db]))


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


(defn req!
  "Make Telegram API calls"
  [method api-name params]
  (let [url  (format "https://api.telegram.org/bot%s/%s"
               config/TGTOKEN
               api-name)
        res  (-req! method url (if (= method :get)
                                 {:query-params params}
                                 {:body    (json/write-value-as-string params)
                                  :headers {"Content-Type" "application/json"}}))
        data (-> res
                 :body
                 (json/read-value json/keyword-keys-object-mapper))]
    (if (:error_code data)
      (throw (ex-info (:description data) {:response res}))
      (with-meta data {:response res}))))


(defn req-file!
  "Get a file from Telegram"
  [file-path]
  (let [url (format "https://api.telegram.org/file/bot%s/%s"
              config/TGTOKEN
              file-path)]
    (-req! :get url nil)))


(defn reply [message text & [{:keys [nopreview
                                     forcereply]}]]
  (let [chat-id (if (map? message)
                  (-> message :chat :id)
                  message)
        opts    (cond-> {:chat_id    chat-id
                         :parse_mode "HTML"
                         :text       text}
                  nopreview  (assoc :disable_web_page_preview true)
                  forcereply (assoc :reply_markup {:force_reply true}))]
    (u/log ::reply :chat-id chat-id :text text)
    (req! :post "sendMessage" opts)))


;;; logic

(defn process-bundle [message]
  (auth/with-user (auth/user-by-chat (-> message :chat :id))
    (let [start (System/currentTimeMillis)

          file-id  (-> message :document :file_id)
          filemeta (req! :get "getFile" {:file_id file-id})

          zip    (:body (req-file! (-> filemeta :result :file_path)))
          bundle (bundle/read zip "src/media/")

          results (github/upload-tb! bundle)
          _       (u/log ::published :results results)

          duration (- (System/currentTimeMillis) start)
          report   (if (empty? results)
                     (format "nothing changed, %s ms"
                       duration)
                     (format "successfully published %s files at [%s](%s) in %s ms"
                       (count results)
                       (-> results last :commit :sha)
                       (-> results last :content :html_url)
                       duration))]
      (reply message report))))


(defn process-start [message]
  (let [url (auth/oauth-url {:chat_id  (-> message :chat :id)
                             :telegram (-> message :chat :username)})]
    (reply message
      (format "<a href=\"%s\">Login with Github</a>" url)
      {:nopreview true})))


(defn process-repo [message]
  (let [user (auth/user-by-chat (-> message :chat :id))
        repo (:text message)
        info (auth/with-user user
               (github/req! :get (format "/repos/%s/%s" (:github user) repo) nil))]
    (if info
      (do
        (db/one {:update :users
                 :set    {:repo (:name info)}
                 :where  [:= :github (:github user)]})
        (reply message
          "Success! Now send me compressed TextBundles so I can publish them for you!"))
      (reply message
        (format "Could not find repo with name %s/%s" (:github user) repo)))))


(defn reply-to? [message pattern]
  (and (:reply_to_message message)
       (->> message
            :reply_to_message
            :text
            (re-find (re-pattern (format "\\b%s\\b" pattern))))))


(defn process-update [upd]
  (u/log ::process :data upd)
  (let [message (:message upd)]
    (cond
      (-> message :document :file_id) (process-bundle message)
      (= (-> message :text) "/start") (process-start message)
      (reply-to? message "repo")      (process-repo message))))


(defn get-updates-or-else [update-id]
  (try
    (req! :get "getUpdates"
      (cond-> {:timeout 60}
        ;; https://core.telegram.org/bots/api#getupdates
        ;; "must be greater by one than the highest ..."
        update-id (assoc :offset (inc update-id))))
    (catch Exception _
      (Thread/sleep 10))))


(defn start-poll []
  (let [stop (atom false)
        t    (Thread.
               (fn []
                 (loop [update-id nil]
                   (u/log ::poll :update-id update-id)
                   (if @stop
                     (u/log ::stop)
                     (let [updates (get-updates-or-else update-id)
                           new-id  (-> updates :result last :update_id)]
                       (run! process-update (:result updates))
                       (recur (or new-id update-id)))))))]
    (.start t)
    #(reset! stop true)))
