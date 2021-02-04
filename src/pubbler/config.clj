(ns pubbler.config
  (:require [clojure.string :as str]))


(defn get-env [var-name msg]
  (or (some-> (System/getenv var-name) str/trim)
      (binding [*out* *err*]
        (println msg))))


(def PORT     #(or (some-> (System/getenv "PORT") str/trim Integer/parseInt)
                   9000))
(def TGTOKEN  #(get-env "TGTOKEN"
                 "TGTOKEN env var is empty, please set to Telegram bot token"))
(def PGURL    #(get-env "PGURL"
                 "PGURL env var is empty, please set to Postgres URL"))
(def DOMAIN   #(get-env "DOMAIN"
                 "DOMAIN env var is empty, please set to site domain"))
(def GHID     #(get-env "GHID"
                 "GHID env var is empty, please set to Github app OAuth id"))
(def GHSECRET #(get-env "GHSECRET"
                 "GHSECRET env var is empty, please set to Github app OAuth secret"))
