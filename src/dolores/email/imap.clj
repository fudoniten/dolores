(ns dolores.email.imap
  (:require [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :refer [DoloresEmailService] :as email])
  (:import (javax.mail Session Folder Message$RecipientType)))

(defn pthru [o] (clojure.pprint/pprint o) o)

(defprotocol RawEmailOperations
  "Protocol for raw email operations."
  (search-emails [this since])
  (get-email-content [this email-id]))

(defrecord ImapService [store]
  RawEmailOperations
  (search-emails [_ since]
    (let [inbox (.getFolder store "INBOX")]
      (.open inbox Folder/READ_ONLY)
      (let [since-date (java.util.Date. (.getTime since))
            search-term (javax.mail.search.ReceivedDateTerm. javax.mail.search.ComparisonTerm/GT since-date)]
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

(defn connect!
  [{:keys [::host ::user ::password]}]
  (->ImapService (-create-connection! host user password)))

(defn parse-email
  "Converts a javax.mail.Message to the internal email format."
  [msg]
  (let [header {::email/to (or (first (.getRecipients msg Message$RecipientType/TO)) "")
                ::email/from (or (first (.getFrom msg)) "")
                ::email/subject (or (.getSubject msg) "")
                ::email/cc (or (map str (.getRecipients msg Message$RecipientType/CC)) [])
                ::email/bcc (or (map str (.getRecipients msg Message$RecipientType/BCC)) [])
                ::email/sent-date (or (.getSentDate msg) (java.util.Date.))
                ::email/received-date (or (.getReceivedDate msg) (java.util.Date.))
                ::email/spam-score 0.0 ;; Default spam score
                ::email/server-info "IMAP Server"}
        body (.getContent msg)
        email {::email/header header ::email/body body ::email/attachments []}] ;; Add logic for attachments if needed
    (if (s/valid? ::email/email-full email)
      email
      (throw (ex-info "Invalid email" {:email email})))))

(defrecord IMAPServiceWrapper [raw-ops]
  DoloresEmailService
  (get-headers [_ since]
    (try
      (let [messages (search-emails raw-ops since)]
        (map parse-email messages))
      (catch Exception e
        (log/error e "Failed to fetch email headers"))))

  (get-email [_ email-id]
    (try
      (let [msg (get-email-content raw-ops email-id)
        (parse-email msg)
      (catch Exception e
        (log/error e "Failed to fetch email"))))

  (get-emails [_ since]
    (try
      (let [messages (search-emails raw-ops since)]
        (map (fn [msg]
        (parse-email msg)
             messages))
      (catch Exception e
        (log/error e "Failed to fetch emails")))))
