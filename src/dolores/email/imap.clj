(ns dolores.email.imap
  (:require [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :refer [DoloresEmailService] :as email])
  (:import (javax.mail Session Store Folder Flags Flags$Flag)
           (javax.mail.search FlagTerm)))

(defrecord IMAPService [host user password]
  DoloresEmailService
  (get-headers [_ since]
    (try
      (let [props (System/getProperties)]
        (.put props "mail.store.protocol" "imaps")
        (let [session (Session/getDefaultInstance props nil)
              store (.getStore session "imaps")]
          (.connect store host user password)
          (let [inbox (.getFolder store "INBOX")]
            (.open inbox Folder/READ_ONLY)
            (let [since-date (java.util.Date. (.getTime since))
                  search-term (javax.mail.search.ReceivedDateTerm. javax.mail.search.ComparisonTerm/GT since-date)
                  messages (.search inbox search-term)]
              (map (fn [msg]
                     (let [header {::email/to (or (first (.getRecipients msg javax.mail.Message$RecipientType/TO)) "")
                                   ::email/from (or (first (.getFrom msg)) "")
                                   ::email/subject (or (.getSubject msg) "")
                                   ::email/cc (or (map str (.getRecipients msg javax.mail.Message$RecipientType/CC)) [])
                                   ::email/bcc (or (map str (.getRecipients msg javax.mail.Message$RecipientType/BCC)) [])
                                   ::email/sent-date (or (.getSentDate msg) (java.util.Date.))
                                   ::email/received-date (or (.getReceivedDate msg) (java.util.Date.))
                                   ::email/spam-score 0.0 ;; Default spam score
                                   ::email/server-info "IMAP Server"}]
                       (if (s/valid? ::email/email-header header)
                         header
                         (throw (ex-info "Invalid email header" {:header header})))))
                   messages)))))
      (catch Exception e
        (log/error e "Failed to fetch email headers"))))

  (get-email [_ email-id]
    (try
      (let [props (System/getProperties)]
        (.put props "mail.store.protocol" "imaps")
        (let [session (Session/getDefaultInstance props nil)
              store (.getStore session "imaps")]
          (.connect store host user password)
          (let [inbox (.getFolder store "INBOX")]
            (.open inbox Folder/READ_ONLY)
            (let [msg (first (filter #(= (.getMessageID %) email-id) (.getMessages inbox)))
                  header {::email/to (.getRecipients msg javax.mail.Message$RecipientType/TO)
                          ::email/from (.getFrom msg)
                          ::email/subject (.getSubject msg)
                          ::email/cc (.getRecipients msg javax.mail.Message$RecipientType/CC)
                          ::email/bcc (.getRecipients msg javax.mail.Message$RecipientType/BCC)
                          ::email/sent-date (.getSentDate msg)
                          ::email/received-date (.getReceivedDate msg)
                          ::email/spam-score 0.0 ;; Default spam score
                          ::email/server-info "IMAP Server"}
                  body (.getContent msg)
                  email {::email/header header ::email/body body ::email/attachments []}] ;; Add logic for attachments if needed
              (if (s/valid? ::email/email-full email)
                email
                (throw (ex-info "Invalid email" {:email email})))))))
      (catch Exception e
        (log/error e "Failed to fetch email"))))

  (get-emails [_ since]
    (try
      (let [props (System/getProperties)]
        (.put props "mail.store.protocol" "imaps")
        (let [session (Session/getDefaultInstance props nil)
              store (.getStore session "imaps")]
          (.connect store host user password)
          (let [inbox (.getFolder store "INBOX")]
            (.open inbox Folder/READ_ONLY)
            (let [since-date (java.util.Date. (.getTime since))
                  search-term (javax.mail.search.ReceivedDateTerm. javax.mail.search.ComparisonTerm/GT since-date)
                  messages (.search inbox search-term)]
              (map (fn [msg]
                     (let [header {::email/to (.getRecipients msg javax.mail.Message$RecipientType/TO)
                                   ::email/from (.getFrom msg)
                                   ::email/subject (.getSubject msg)
                                   ::email/cc (.getRecipients msg javax.mail.Message$RecipientType/CC)
                                   ::email/bcc (.getRecipients msg javax.mail.Message$RecipientType/BCC)
                                   ::email/sent-date (.getSentDate msg)
                                   ::email/received-date (.getReceivedDate msg)
                                   ::email/spam-score 0.0 ;; Default spam score
                                   ::email/server-info "IMAP Server"}
                           body (.getContent msg)
                           email {::email/header header ::email/body body ::email/attachments []}] ;; Add logic for attachments if needed
                       (if (s/valid? ::email/email-full email)
                         email
                         (throw (ex-info "Invalid email" {:email email})))))
                   messages)))))
      (catch Exception e
        (log/error e "Failed to fetch emails")))))
