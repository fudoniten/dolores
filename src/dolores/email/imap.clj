(ns dolores.email.imap
  (:require [clojure.tools.logging :as log]
            [dolores.utils :refer [verify-args]]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :refer [DoloresEmailService] :as email])
  (:import (javax.mail Session Folder Message$RecipientType Message)
           (javax.mail.search ReceivedDateTerm ComparisonTerm)
           java.util.Date
           java.time.Instant))

(defprotocol RawEmailOperations
  "Protocol for raw email operations."
  (search-emails [this ^java.time.Instant since])
  (get-email-content [this email-id]))

(defrecord RawImapService [store]
  RawEmailOperations
  (search-emails [_ since]
    (let [inbox (.getFolder store "INBOX")]
      (.open inbox Folder/READ_ONLY)
      (let [since-date (Date/from since)
            search-term (ReceivedDateTerm. ComparisonTerm/GT since-date)]
        (.search inbox search-term))))

  (get-email-content [_ email-id]
    (let [inbox (.getFolder store "INBOX")]
      (.open inbox Folder/READ_ONLY)
      (first (filter #(= (.getMessageID %) email-id) (.getMessages inbox))))))

(defn -create-connection!
  [host user password]
  (let [props (System/getProperties)]
    (.put props "mail.store.protocol" "imaps")
    (let [session (Session/getDefaultInstance props nil)
          store   (.getStore session "imaps")]
      (.connect store host user password)
      store)))

(s/fdef parse-email
  :args (s/cat :msg (partial instance? Message))
  :ret (s/nilable ::email/email-full))
(defn parse-email
  "Converts a javax.mail.Message to the internal email format."
  [msg]
  (let [header {::email/to (str (or (first (.getRecipients msg Message$RecipientType/TO)) ""))
                ::email/from (str (or (first (.getFrom msg)) ""))
                ::email/subject (or (.getSubject msg) "")
                ::email/cc (vec (or (map str (.getRecipients msg Message$RecipientType/CC)) []))
                ::email/bcc (vec (or (map str (.getRecipients msg Message$RecipientType/BCC)) []))
                ::email/sent-date (or (some-> msg
                                              (.getSentDate)
                                              (.toInstant))
                                      (Instant/now))
                ::email/received-date (or (some-> msg
                                                  (.getReceivedDate)
                                                  (.toInstant))
                                          (Instant/now))
                ::email/spam-score 0.0 ;; Default spam score
                ::email/server-info "IMAP Server"}
        body (.getContent msg)
        email {::email/header header ::email/body body ::email/attachments []}] ;; Add logic for attachments if needed
    (if (s/valid? ::email/email-full email)
      email
      (do (s/explain ::email/email-full email)
          (throw (ex-info "Invalid email" {:email email}))))))

(defrecord ImapService [raw-service]
  DoloresEmailService

  (get-email [_ email-id]
    (try
      (parse-email (get-email-content raw-service email-id))
      (catch Exception e
        (log/error e "Failed to fetch email"))))

  (get-emails [_ ^java.time.Instant since]
    (try
      (map parse-email (search-emails raw-service since))
      (catch Exception e
        (log/error e "Failed to fetch emails")))))

(s/fdef connect!
  :args (s/keys* :req-un [::email/host ::email/user ::email/password])
  :ret ImapService)
(defn connect!
  [& {:keys [::host ::user ::password]}]
  (verify-args {:host host :user user :password password} [:host :user :password])
  (->ImapService (->RawImapService (-create-connection! host user password))))
