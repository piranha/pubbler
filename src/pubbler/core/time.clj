(ns pubbler.core.time
  (:import [java.time LocalDate]
           [java.time.format DateTimeFormatter]))


(set! *warn-on-reflection* true)


(defn parse [^String s]
  (LocalDate/parse s DateTimeFormatter/ISO_LOCAL_DATE))


(defn today []
  (LocalDate/now))


(defn strftime [^String fmt t]
  (let [fmt (.replaceAll fmt "%([a-zA-Z])" "%1\\$t$1")]
    (format fmt t)))
