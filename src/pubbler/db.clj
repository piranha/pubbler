(ns pubbler.db
  (:require [mount.core :as mount]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as jdbc-rs]
            [next.jdbc.date-time :as jdbc-dt]
            [honeysql.core :as honeysql]
            [honeysql.format :as honeyfmt]
            [honeysql-postgres.format]  ;; do not remove
            [pubbler.config :as config]
            [clojure.string :as str])
  (:import [java.net URI]
           [com.zaxxer.hikari HikariConfig HikariDataSource]))


(set! *warn-on-reflection* true)
(jdbc-dt/read-as-local)


(def raw honeysql/raw)
(def call honeysql/call)
(def fmt honeysql/format)
(def value honeyfmt/value)


(defn make-pool [url]
  (let [uri        (URI. url)
        ;; Hikari does not support user/password in url :(
        jdbc-url   (format "jdbc:%s://%s:%s%s"
                     (let [scheme (.getScheme uri)]
                       (if (= scheme "postgres")
                         "postgresql"
                         scheme))
                     (.getHost uri)
                     (.getPort uri)
                     (.getPath uri))
        init-sql   "SET application_name TO 'pubbler'"
        [user pwd] (str/split (.getUserInfo uri) #":")
        config     (doto (HikariConfig.)
                     (.setJdbcUrl jdbc-url)
                     (.setUsername user)
                     (.setPassword pwd)
                     (.setMaximumPoolSize 3)
                     (.setConnectionTimeout 5000)
                     (.setRegisterMbeans true)
                     (.setConnectionInitSql init-sql)
                     (.addDataSourceProperty "prepareThreshold" 0))]
    (HikariDataSource. config)))


(mount/defstate conn
  :start (make-pool (config/PGURL))
  :stop (.close ^HikariDataSource conn))


(defn format-query [query]
  (if (string? query)
    [query]
    (honeysql/format query :quoting :ansi)))


(defn q [query]
  (jdbc/execute! conn (format-query query)
    {:builder-fn jdbc-rs/as-unqualified-lower-maps}))


(defn one [query]
  (first (q query)))
