(ns dolores.email.gmail
  (:require [clojure.tools.logging :as log]
            [dolores.utils :refer [verify-args]]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :refer [DoloresEmailService] :as email])
  (:import (com.google.api.services.gmail Gmail$Builder)
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
      (do (s/explain-data ::email/email-full email)
          (throw (ex-info "Invalid email" {:email email}))))))

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

(defn token-age
  "Calculates the age of the token in milliseconds."
  [credential]
  (let [creation-time (.getExpirationTimeMilliseconds credential)]
    (- (System/currentTimeMillis) creation-time)))

(defn refresh-gmail-token
  "Refreshes the Gmail API access token using the provided credential object."
  [credential]
  ;; Function to refresh Gmail API access token
  (try
    (.refreshToken credential)
    (log/info "Gmail access token refreshed successfully.")
    credential
    (catch Exception e
      (log/error e "Failed to refresh Gmail access token"))))

(defn refresh-token-if-needed
  "Refreshes the token if it is older than the specified max-age."
  [credential max-age]
  (when (> (token-age credential) max-age)
    (refresh-gmail-token credential))
  credential)

(defrecord RawGmailService [service user-id]
  RawGmailOperations
  (fetch-email [this email-id]
    (when (not (.isTokenValid? service))
      (refresh-gmail-token (.getCredentials service)))
    (.execute (.users.messages.get service user-id email-id)))

  (fetch-emails [this query]
    (when (not (.isTokenValid? service))
      (refresh-gmail-token (.getCredentials service)))
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

(defn connect!
  "Authenticates with Gmail and creates a RawGmailService."
  [& {:keys [client-id client-secret user-id]}]
  (verify-args {:client-id client-id :client-secret client-secret :user-id user-id} [:client-id :client-secret :user-id])
  (let [credentials (get-gmail-credentials client-id client-secret)
        ;; 1 hr in ms
        max-token-age (* 60 60 1000)]
    (refresh-token-if-needed credentials max-token-age)
    (let [service (Gmail$Builder. (GoogleNetHttpTransport/newTrustedTransport)
                                  (JacksonFactory/getDefaultInstance)
                                  credentials)
          gmail-service (.build service)]
      (->RawGmailService gmail-service user-id))))
