(ns dolores.utils.storage
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [java.time.format DateTimeFormatter]
            [java.time LocalDateTime]))

(defn- current-datetime []
  "Get the current date and time as a formatted string."
  (let [formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd-HH-mm-ss")]
    (.format (LocalDateTime/now) formatter)))

(defn- ensure-dir-exists [dir]
  "Ensure that the directory exists, creating it if necessary."
  (let [file (io/file dir)]
    (when-not (.exists file)
      (.mkdirs file))))

(defn save-summary [base-dir summary-type data]
  "Save a summary to a file with a timestamped name."
  (let [datetime (current-datetime)
        dir (str base-dir "/" (subs datetime 0 4) "/" (subs datetime 5 7))
        filename (str summary-type "-" datetime ".edn")
        filepath (str dir "/" filename)]
    (ensure-dir-exists dir)
    (write-edn-file filepath data)))

(defn cleanup-old-files [base-dir days-old]
  "Delete files older than the specified number of days."
  (let [cutoff-time (- (.getTime (java.util.Date.)) (* days-old 24 60 60 1000))]
    (doseq [file (file-seq (io/file base-dir))]
      (when (and (.isFile file) (< (.lastModified file) cutoff-time))
        (.delete file)))))

(defn read-edn-file [file-path]
  "Read data from an EDN file."
  (with-open [r (io/reader file-path)]
    (edn/read r)))

(defn write-edn-file [file-path data]
  "Write data to an EDN file."
  (with-open [w (io/writer file-path)]
    (binding [*print-dup* true]
      (prn data w))))
