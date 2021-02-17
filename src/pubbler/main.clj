(ns pubbler.main
  (:gen-class)
  (:require [mount.core :as mount]
            [org.httpkit.client :as http]
            [org.httpkit.sni-client :as sni-client]

            [pubbler.log :as log]
            [pubbler.telegram :as telegram]
            [pubbler.httpd]))


(set! *warn-on-reflection* true)
(alter-var-root #'http/*default-client* (fn [_] sni-client/default-client))
(log/conf! {:logger (log/->Stdout)})


(mount/defstate poller
  :start (telegram/start-poll)
  :stop (poller))


(defn -main [& args]
  (mount/start))
