(ns pubbler.config
  (:require [clojure.string :as str]))


(defn err [msg]
  (binding [*out* *err*]
    (println msg)))

(def PORT (or (some-> (System/getenv "PORT") str/trim Integer/parseInt)
              9000))
(def TGTOKEN (or (some-> (System/getenv "TGTOKEN") str/trim)
                 (err "TGTOKEN env var is empty, please set to Telegram bot token")))
(def PGURL (or (some-> (System/getenv "PGURL") str/trim)
               (err "PGURL env var is empty, please set to Postgres URL")))
(def DOMAIN (or (some-> (System/getenv "DOMAIN") str/trim)
               (err "DOMAIN env var is empty, please set to site domain")))
(def GHID (or (some-> (System/getenv "GHID") str/trim)
               (err "GHID env var is empty, please set to Github app OAuth id")))
(def GHSECRET (or (some-> (System/getenv "GHSECRET") str/trim)
               (err "GHSECRET env var is empty, please set to Github app OAuth secret")))
