(ns pubbler.telegram
  (:require [clojure.string :as str]
            [clojure.core.strint :refer [<<]]
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
      (throw (ex-info (:description data) {:data     data
                                           :response res}))
      (with-meta data {:response res}))))


(defn req-file!
  "Get a file from Telegram"
  [file-path]
  (let [url (format "https://api.telegram.org/file/bot%s/%s"
              config/TGTOKEN
              file-path)]
    (-req! :get url nil)))


(defn reply [message text & [{:keys [nopreview
                                     forcereply
                                     edit]}]]
  (let [chat-id (if (map? message)
                  (-> message :chat :id)
                  message)
        opts    (cond-> {:chat_id    chat-id
                         :message_id edit
                         :parse_mode "HTML"
                         :text       text}
                  nopreview  (assoc :disable_web_page_preview true)
                  forcereply (assoc :reply_markup {:force_reply true}))]
    (u/log ::reply :opts opts)
    (if (:message_id opts)
      (req! :post "editMessageText" opts)
      (req! :post "sendMessage" opts))))


(defn parse-mapping [mapping]
  (let [[pat dest] (str/split mapping #"=>" 2)]
    [(re-pattern (str/trim pat))
     (str/trim dest)]))


;;; logic

(defn process-bundle [message]
  (let [progress (reply message "<i>Starting upload...</i>")
        msgid    (-> progress :result :message_id)

        start (System/currentTimeMillis)
        user  github/*user*

        file-id  (-> message :document :file_id)
        filemeta (req! :get "getFile" {:file_id file-id})

        zip    (:body (req-file! (-> filemeta :result :file_path)))
        bundle (bundle/read zip)

        [path updates] (github/upload-tb! bundle)
        post-url       (when (:mapping user)
                         (apply str/replace path
                           (parse-mapping (:mapping user))))
        _              (doseq [[link _] updates]
                         (reply message (<< "<code>~{link}</code> done")
                           {:nopreview true
                            :edit      msgid}))
        gh-url         (-> updates last second :content :html_url)
        _              (u/log ::published :results updates)

        duration (- (System/currentTimeMillis) start)
        report   (str
                   (when post-url
                     (<< "<a href=\"~{post-url}\">~(:slug bundle)</a>. "))
                   (if (empty? (filter second updates))
                     "Nothing changed"
                     (<< "Successfully <a href=\"~{gh-url}\">published</a> ~(count (filter second updates)) files"))
                   (<< "\n~{duration} ms"))]
    (reply message report
      {:nopreview true
       :edit      msgid})))


(defn start-auth [message]
  (let [url (auth/oauth-url {:chat_id  (-> message :chat :id)
                             :telegram (-> message :chat :username)})]
    (reply message
      (format "<a href=\"%s\">Login with Github</a>" url)
      {:nopreview true})))


(defn start-setup [message]
  (let [user github/*user*]
    (reply (-> message :chat :id)
      (str (<< "Hey ~(:github user)!")
        (when (or (:repo user) (:path user))
          (<< " Your current settings: repo - <code>~(:repo user)</code>, path - <code>~(:path user)</code>."))
         " Please tell me your target repo name.")
      {:forcereply true})))


(defn process-repo [message]
  (let [user github/*user*
        repo (:text message)
        info (github/req! :get (<< "/repos/~(:github user)/~{repo}") nil)]
    (if info
      (do
        (db/one {:update :users
                 :set    {:repo (:name info)}
                 :where  [:= :github (:github user)]})
        (reply message
          (<<  "Okay, so it's <a href=\"https://github.com/~(:github user)/~{repo}\">~(:github user)/~{repo}</a>.
Please now tell me <b>where to put your posts</b> (and images), i.e. <code>src/blog/%Y</code>. Note that you can use <a href=\"https://man7.org/linux/man-pages/man3/strftime.3.html#DESCRIPTION\">strftime</a> date formatting here.")
          {:forcereply true}))
      (reply message
        "Could not find repo with name ~(:github user)/~{repo}"))))


(defn process-path [message]
  (let [user github/*user*
        path (:text message)]
    (db/one {:update :users
             :set    {:path path}
             :where  [:= :github (:github user)]})
    (reply message
      (<<  "Okay, it's <code>~{path}</code>. Note: you can reply to any question again to change your setting.
Now send me those Bear notes so I can post them for you!

Also, an optional step: if you send a <b>mapping</b> from source path to an url I'll send you generated url on every update. Format should be <code>src/(.*) => https://solovyov.net/$1</code> - you can use regular expressions, obviously. Argument passed for replacement will be in a form <code>~{path}/{slug}/</code>.")
      {:forcereply true})))


(defn process-mapping [message]
  (let [user    github/*user*
        mapping (:text message)]
    (try
      (parse-mapping mapping)
      (db/one {:update :users
               :set    {:mapping mapping}
               :where  [:= :github (:github user)]})
      (reply message
        (<< "Cool! Your mapping is <code>~{mapping}</code> now. Bring those notes here!"))
      (catch Exception e
        (reply message
          (<< "Couldn't parse your mapping: ~(str e)
Please fix it!")
          {:forcereply true})))))


(defn reply-to? [message re]
  (and (:reply_to_message message)
       (->> message
            :reply_to_message
            :text
            (re-find re))))


(defn process-update [upd]
  (u/log ::process :data upd)
  (let [message (:message upd)
        user    (auth/user-by-chat (-> message :chat :id))]
    (auth/with-user user
      (cond
        (-> message :document :file_id)                (process-bundle message)
        (= (-> message :text) "/start")                (start-auth message)
        (= (-> message :text) "/setup")                (start-setup message)
        (reply-to? message #"\brepo\b")                (process-repo message)
        (reply-to? message #"where to put your posts") (process-path message)
        (reply-to? message #"mapping")                 (process-mapping message)
        :else
        (reply message
          "Can't understand you. Please reply to a question or, if you want to restart, send <code>/setup</code>")))))


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
