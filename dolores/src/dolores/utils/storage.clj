(ns dolores.utils.storage
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [java.time.format DateTimeFormatter]
            [java.time LocalDateTime]))

(defn read-edn-file
  "Read data from an EDN file."
  [file-path]
  (with-open [r (io/reader file-path)]
    (edn/read r)))

(defn write-edn-file
  "Write data to an EDN file."
  [file-path data]
  (with-open [w (io/writer file-path)]
    (binding [*print-dup* true]
      (prn data w))))

(defn- current-datetime
  "Get the current date and time as a formatted string."
  []
  (let [formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd-HH-mm-ss")]
    (.format (LocalDateTime/now) formatter)))

(defn- ensure-dir-exists
  "Ensure that the directory exists, creating it if necessary."
  [dir]
  (let [file (io/file dir)]
    (when-not (.exists file)
      (.mkdirs file))))

(defn save-summary
  "Save a summary to a file with a timestamped name."
  [base-dir summary-type data]
  (let [datetime (current-datetime)
        dir (str base-dir "/" (subs datetime 0 4) "/" (subs datetime 5 7))
        filename (str summary-type "-" datetime ".edn")
        filepath (str dir "/" filename)]
    (ensure-dir-exists dir)
    (write-edn-file filepath data)))

(defn cleanup-old-files
  "Delete files older than the specified number of days."
  [base-dir days-old]
  (let [cutoff-time (- (.getTime (java.util.Date.)) (* days-old 24 60 60 1000))]
    (doseq [file (file-seq (io/file base-dir))]
      (when (and (.isFile file) (< (.lastModified file) cutoff-time))
        (.delete file)))))
