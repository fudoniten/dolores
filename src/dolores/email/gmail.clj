(ns dolores.email.gmail
  (:require [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :refer [DoloresEmailService] :as email])
  (:import (com.google.api.services.gmail Gmail$Builder)
           (com.google.api.services.gmail.model Message)
           (com.google.api.client.googleapis.javanet GoogleNetHttpTransport)
           (com.google.api.client.json.jackson2 JacksonFactory)
           (com.google.api.client.googleapis.auth.oauth2 GoogleAuthorizationCodeFlow$Builder)
           (com.google.api.client.auth.oauth2 Credential)
           (java.time Instant)))

(defn verify-args
  "Verifies that all required keys are present in the map."
  [m required-keys]
  (doseq [k required-keys]
    (when (nil? (get m k))
      (throw (ex-info (str "Missing required argument: " k) {:missing-key k})))))

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

(defn connect!
  "Authenticates with Gmail and creates a RawGmailService."
  [{:keys [client-id client-secret user-id]}]
  (verify-args {:client-id client-id :client-secret client-secret :user-id user-id} [:client-id :client-secret :user-id])
  (let [credentials (get-gmail-credentials client-id client-secret)
        service (Gmail$Builder. (GoogleNetHttpTransport/newTrustedTransport)
                                (JacksonFactory/getDefaultInstance)
                                credentials)
        gmail-service (.build service)]
    (->RawGmailService gmail-service user-id)))

(defn refresh-gmail-token
  "Refreshes the Gmail API access token using the provided credential object."
  [credential]
  ;; Function to refresh Gmail API access token
  (try
    (.refreshToken credential)
    (log/info "Gmail access token refreshed successfully.")
    (catch Exception e
      (log/error e "Failed to refresh Gmail access token"))))
