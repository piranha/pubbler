(ns pubbler.main
  (:require [mount.core :as mount]
            [com.brunobonacci.mulog :as u]
            [org.httpkit.client :as http]
            [org.httpkit.sni-client :as sni-client]

            [pubbler.telegram :as telegram]
            [pubbler.httpd :as httpd]))


(alter-var-root #'http/*default-client* (fn [_] sni-client/default-client))


(mount/defstate logger
  :start (u/start-publisher! {:type :console :pretty? true})
  :stop (logger))


(mount/defstate poller
  :start (telegram/start-poll)
  :stop (poller))


(defn -main [& args]
  (mount/start))
