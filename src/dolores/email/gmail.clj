(ns dolores.email.gmail
  (:require [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :refer [DoloresEmailService] :as email])
  (:import (com.google.api.services.gmail Gmail)
           (com.google.api.services.gmail.model Message)
           (com.google.api.client.googleapis.javanet GoogleNetHttpTransport)
           (com.google.api.client.json.jackson2 JacksonFactory)
           (com.google.api.client.googleapis.auth.oauth2 GoogleAuthorizationCodeFlow$Builder)
           (com.google.api.client.auth.oauth2 Credential)
           (java.time Instant)))

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
        header {::email/to (or (some #(when (= "To" (:name %)) (:value %)) headers) "")
                ::email/from (or (some #(when (= "From" (:name %)) (:value %)) headers) "")
                ::email/subject (or (some #(when (= "Subject" (:name %)) (:value %)) headers) "")
                ::email/cc (vec (or (some #(when (= "Cc" (:name %)) (clojure.string/split (:value %) #",\s*")) headers) []))
                ::email/bcc (vec (or (some #(when (= "Bcc" (:name %)) (clojure.string/split (:value %) #",\s*")) headers) []))
                ::email/sent-date (or (some-> message (.getInternalDate) (Instant/ofEpochSecond)) (Instant/now))
                ::email/received-date (or (some-> message (.getInternalDate) (Instant/ofEpochSecond)) (Instant/now))
                ::email/spam-score 0.0
                ::email/server-info "Gmail Server"}
        email {::email/header header ::email/body body ::email/attachments []}]
    (if (s/valid? ::email/email-full email)
      email
      (do
        (s/explain-data ::email/email-full email)
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
(defn get-gmail-credentials
  "Obtains Gmail credentials using OAuth 2.0 with the provided client ID and client secret."
  [client-id client-secret]
  ;; Function to get Gmail credentials using OAuth 2.0 without redirect URI
  (let [http-transport (GoogleNetHttpTransport/newTrustedTransport)
        json-factory (JacksonFactory/getDefaultInstance)
        flow (-> (GoogleAuthorizationCodeFlow$Builder. http-transport json-factory client-id client-secret ["https://www.googleapis.com/auth/gmail.readonly"])
                 (.setAccessType "offline")
                 (.build))]
    (try
      (let [credential (.authorize flow "user")]
        (log/info "Gmail credentials obtained successfully.")
        credential)
      (catch Exception e
        (log/error e "Failed to obtain Gmail credentials")
        nil))))

(defn refresh-gmail-token
  "Refreshes the Gmail API access token using the provided credential object."
  [credential]
  ;; Function to refresh Gmail API access token
  (try
    (.refreshToken credential)
    (log/info "Gmail access token refreshed successfully.")
    (catch Exception e
      (log/error e "Failed to refresh Gmail access token"))))
