(ns dolores.email.gmail
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]
            [dolores.utils :refer [verify-args]]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [dolores.email.protocol :refer [DoloresEmailService] :as email])
  (:import (com.google.api.services.gmail Gmail$Builder)
           (com.google.api.services.gmail.model Message)
           (com.google.api.client.googleapis.javanet GoogleNetHttpTransport)
           (com.google.api.client.json.jackson2 JacksonFactory)
           (com.google.api.client.googleapis.auth.oauth2 GoogleAuthorizationCodeFlow$Builder
                                                         GoogleClientSecrets
                                                         GoogleClientSecrets$Details)
           (com.google.api.client.util.store FileDataStoreFactory)
           (com.google.api.client.extensions.java6.auth.oauth2 AuthorizationCodeInstalledApp)
           (com.google.api.client.extensions.jetty.auth.oauth2 LocalServerReceiver$Builder)
           (java.io File)
           (java.time Instant)
           (java.util Collections)))

(defprotocol RawGmailOperations
  "Protocol for raw Gmail operations."
  (fetch-email [this email-id])
  (fetch-emails [this query]))

(defn body->string
  "Converts the body of a Gmail message to a plain text string."
  [body-part]
  (if (instance? javax.mail.internet.MimeMultipart body-part)
    (let [multipart (javax.mail.internet.MimeMultipart. body-part)]
      (loop [i 0
             text ""]
        (if (< i (.getCount multipart))
          (let [part (.getBodyPart multipart i)]
            (if (= (.getContentType part) "text/plain")
              (recur (inc i) (str text (.getContent part)))
              (recur (inc i) text)))
          text)))
    (.getData body-part)))

(defn parse-gmail-email
  "Converts a Gmail Message to the internal email format."
  [^Message message]
  (s/assert ::email/gmail-message message)
  (let [payload (.getPayload message)
        headers (.getHeaders payload)
        body (or (body->string (.getBody payload)) "")
        header {::email/to (or (some #(when (= "To" (:name %)) (:value %)) headers) "")
                ::email/from (or (some #(when (= "From" (:name %)) (:value %)) headers) "")
                ::email/subject (or (some #(when (= "Subject" (:name %)) (:value %)) headers) "")
                ::email/cc (vec (or (some #(when (= "Cc" (:name %)) (str/split (:value %) #",\s*")) headers) []))
                ::email/bcc (vec (or (some #(when (= "Bcc" (:name %)) (str/split (:value %) #",\s*")) headers) []))
                ::email/sent-date (or (some-> message (.getInternalDate) (Instant/ofEpochMilli)) (Instant/now))
                ::email/received-date (or (some-> message (.getInternalDate) (Instant/ofEpochMilli)) (Instant/now))
                ::email/spam-score 0.0
                ::email/server-info "Gmail Server"}
        email {::email/header header ::email/body body ::email/attachments []}]
    (assert (not (nil? email)) (throw (ex-info "EMAIL IS NIL!" {})))
    (if (s/valid? ::email/email-full email)
      email
      (do (s/explain ::email/email-full email)
          (throw (ex-info "Invalid email" {:email email}))))))

(defn authorization-url [flow redirect-uri]
  (.. flow newAuthorizationUrl (setRedirectUri redirect-uri) build toString))

(s/fdef get-gmail-credentials
  :args (s/cat :client-id ::email/client-id :client-secret ::email/client-secret)
  :ret  ::email/credential)
(defn get-gmail-credentials
  "Obtains Gmail credentials using OAuth 2.0 with the provided client ID and client secret."
  [client-id client-secret]
  ;; Function to get Gmail credentials using OAuth 2.0 without redirect URI
  (let [http-transport (GoogleNetHttpTransport/newTrustedTransport)
        json-factory (JacksonFactory/getDefaultInstance)
        client-deets (doto (GoogleClientSecrets$Details.)
                       (.setClientId client-id)
                       (.setClientSecret client-secret)
                       ;;(.setAuthUri "https://accounts.google.com/o/oauth2/auth")
                       ;;(.setTokenUri "https://oauth2.googleapis.com/token")
                       )
        secrets (doto (GoogleClientSecrets.)
                  (.setInstalled client-deets))
        flow (-> (GoogleAuthorizationCodeFlow$Builder. http-transport json-factory secrets
                                                       (Collections/singleton "https://www.googleapis.com/auth/gmail.readonly")
                 (.setDataStoreFactory (FileDataStoreFactory. (File. "tokens")))
                 (.setAccessType "offline")
                 (.build)))]
    (try
      (println (format "visit the following url to authenticate with google: %s" auth-url))
      (let [credential (.authorize app "user")]
        (log/info "Gmail credentials obtained successfully.")
        credential)
      (catch Exception e
        (throw (ex-info "failed to obtain gmail credentials" {:error e}))))))

(defn token-age
  "Calculates the age of the token in milliseconds."
  [credential]
  (let [creation-time (.getExpirationTimeMilliseconds credential)]
    (- (System/currentTimeMillis) creation-time)))

(s/fdef refresh-gmail-token
  :args (s/cat :credential ::email/credential)
  :ret ::email/credential)
(defn refresh-gmail-token
  "Refreshes the Gmail API access token using the provided credential object."
  [credential]
  ;; Function to refresh Gmail API access token
  (try
    (.refreshToken credential)
    (log/info "Gmail access token refreshed successfully.")
    credential
    (catch Exception e
      (throw (ex-info "failed to refresh gmail access token" {:error e})))))

(s/fdef refresh-token-if-needed
  :args (s/cat :credential ::email/credential :max-age ::email/max-age)
  :ret ::email/credential)
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

  (get-emails [_ ^java.time.Instant since]
    (try
      (let [query (str "after:" (.toEpochMilli since))]
        (map parse-gmail-email (fetch-emails raw-service query)))
      (catch Exception e
        (log/error e "Failed to fetch emails")))))

(s/fdef connect!
  :args (s/keys* :req-un [::email/client-id ::email/client-secret ::email/user-id])
  :ret RawGmailService)

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

;; Instrument the spec'd functions
(stest/instrument)
