(ns pubbler.main
  (:require [mount.core :as mount]
            [com.brunobonacci.mulog :as u]))


(mount/defstate logger
  :start (u/start-publisher! {:type :console})
  :stop (logger))


(defn -main [& args]
  (u/log ::start))
