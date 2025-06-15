(ns dolores.email.imap
  (:require [clojure.tools.logging :as log]
            [dolores.email.protocol :refer :all])
  (:import (javax.mail Session Store Folder Flags)
           (javax.mail.search FlagTerm)))

(defrecord IMAPService [host user password]
  DoloresEmailService
  (get-headers [this since]
    (try
      (let [props (System/getProperties)]
        (.put props "mail.store.protocol" "imaps")
        (let [session (Session/getDefaultInstance props nil)
              store (.getStore session "imaps")]
          (.connect store host user password)
          (let [inbox (.getFolder store "INBOX")]
            (.open inbox Folder/READ_ONLY)
            (let [messages (.getMessages inbox)]
              (map #(str (.getSubject %)) messages)))))
      (catch Exception e
        (log/error e "Failed to fetch email headers"))))

  (get-email [this email-id]
    (try
      ;; Implement logic to fetch full email content by ID
      (log/info "Fetched email successfully.")
      ;; Return email content
      )
    (catch Exception e
      (log/error e "Failed to fetch email"))))

  (get-emails [this since]
    (try
      (let [props (System/getProperties)]
        (.put props "mail.store.protocol" "imaps")
        (let [session (Session/getDefaultInstance props nil)
              store (.getStore session "imaps")]
          (.connect store host user password)
          (let [inbox (.getFolder store "INBOX")]
            (.open inbox Folder/READ_ONLY)
            (let [messages (.getMessages inbox)]
              (map #(str (.getSubject %)) messages)))))
      (catch Exception e
        (log/error e "Failed to fetch emails")))))
