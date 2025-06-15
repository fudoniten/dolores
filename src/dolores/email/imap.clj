(ns dolores.email.imap
  (:require [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :refer [DoloresEmailService ::email-header ::email-full]])
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
            (let [messages (.search inbox (FlagTerm. (Flags. Flags$Flag/SEEN) false))]
              (map (fn [msg]
                     (let [header {:to (or (first (.getRecipients msg javax.mail.Message$RecipientType/TO)) "")
                                   :from (or (first (.getFrom msg)) "")
                                   :subject (or (.getSubject msg) "")
                                   :cc (or (map str (.getRecipients msg javax.mail.Message$RecipientType/CC)) [])
                                   :bcc (or (map str (.getRecipients msg javax.mail.Message$RecipientType/BCC)) [])
                                   :sent-date (or (.getSentDate msg) (java.util.Date.))
                                   :received-date (or (.getReceivedDate msg) (java.util.Date.))
                                   :spam-score 0.0 ;; Default spam score
                                   :server-info "IMAP Server"}]
                       (if (s/valid? ::email-header header)
                         header
                         (throw (ex-info "Invalid email header" {:header header})))))
                   messages)))))
      (catch Exception e
        (log/error e "Failed to fetch email headers"))))

  (get-email [this email-id]
    (try
      ;; Implement logic to fetch full email content by ID
      (let [msg (first (filter #(= (.getMessageID %) email-id) (.getMessages inbox)))
            header {:to (.getRecipients msg javax.mail.Message$RecipientType/TO)
                    :from (.getFrom msg)
                    :subject (.getSubject msg)
                    :cc (.getRecipients msg javax.mail.Message$RecipientType/CC)
                    :bcc (.getRecipients msg javax.mail.Message$RecipientType/BCC)
                    :sent-date (.getSentDate msg)
                    :received-date (.getReceivedDate msg)
                    :spam-score 0.0 ;; Default spam score
                    :server-info "IMAP Server"}
            body (.getContent msg)
            email {:header header :body body :attachments []}] ;; Add logic for attachments if needed
        (if (s/valid? ::email-full email)
          email
          (throw (ex-info "Invalid email" {:email email}))))
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
              (map (fn [msg]
                     (let [header {:to (.getRecipients msg javax.mail.Message$RecipientType/TO)
                                   :from (.getFrom msg)
                                   :subject (.getSubject msg)
                                   :cc (.getRecipients msg javax.mail.Message$RecipientType/CC)
                                   :bcc (.getRecipients msg javax.mail.Message$RecipientType/BCC)
                                   :sent-date (.getSentDate msg)
                                   :received-date (.getReceivedDate msg)
                                   :spam-score 0.0 ;; Default spam score
                                   :server-info "IMAP Server"}
                           body (.getContent msg)
                           email {:header header :body body :attachments []}] ;; Add logic for attachments if needed
                       (if (s/valid? ::email-full email)
                         email
                         (throw (ex-info "Invalid email" {:email email})))))
                   messages)))))
      (catch Exception e
        (log/error e "Failed to fetch emails")))))
