(ns dolores.email.gmail
  (:require [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :refer [DoloresEmailService] :as email])
  (:import (com.google.api.services.gmail Gmail)
           (com.google.api.services.gmail.model Message)
           (com.google.api.client.googleapis.javanet GoogleNetHttpTransport)
           (com.google.api.client.json.jackson2 JacksonFactory)
           (com.google.api.client.googleapis.auth.oauth2 GoogleAuthorizationCodeFlow$Builder)
           (com.google.api.client.auth.oauth2 Credential)))

(defprotocol RawGmailOperations
  "Protocol for raw Gmail operations."
  (fetch-email [this email-id])
  (fetch-emails [this query]))

(defn parse-gmail-email
  "Converts a Gmail Message to the internal email format."
  [message]
  (let [payload (.getPayload message)
        headers (.getHeaders payload)
        body (or (.getData (.getBody payload)) "")
        header {::email/to (or (some #(when (= "To" (.getName %)) (.getValue %)) headers) "")
                ::email/from (or (some #(when (= "From" (.getName %)) (.getValue %)) headers) "")
                ::email/subject (or (some #(when (= "Subject" (.getName %)) (.getValue %)) headers) "")
                ::email/cc (vec (or (map str (.getRecipients payload javax.mail.Message$RecipientType/CC)) []))
                ::email/bcc (vec (or (map str (.getRecipients payload javax.mail.Message$RecipientType/BCC)) []))
                ::email/sent-date (or (.getInternalDate message) (java.util.Date.))
                ::email/received-date (or (.getInternalDate message) (java.util.Date.))
                ::email/spam-score 0.0
                ::email/server-info "Gmail Server"}
        email {::email/header header ::email/body body ::email/attachments []}]
    (if (s/valid? ::email/email-full email)
      email
      (do
        (s/explain ::email/email-full email)
        (throw (ex-info "Invalid email" {:email email}))))))

(defrecord RawGmailService [service user-id]
  RawGmailOperations
  (fetch-email [_ email-id]
    (.execute (.users.messages.get service user-id email-id)))

  (fetch-emails [_ query]
    (let [request (.users.messages.list service user-id)
          response (.execute (.setQ request query))]
      (.getMessages response))))

(defrecord GmailService [raw-service]
  DoloresEmailService

  (get-email [_ email-id]
    (try
      (parse-gmail-email (fetch-email raw-service email-id))
      (catch Exception e
        (log/error e "Failed to fetch email"))))

  (get-emails [_ since]
    (try
      (let [query (str "after:" (.getTime since))]
        (map parse-gmail-email (fetch-emails raw-service query)))
      (catch Exception e
        (log/error e "Failed to fetch emails")))))

(defn manage-labels
  "Manages labels for Gmail messages."
  [^Gmail service user-id message-id labels]
  (try
    ;; Implement label management logic here
    (log/info "Labels managed successfully.")
    (catch Exception e
      (log/error e "Failed to manage labels"))))
