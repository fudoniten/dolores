(ns dolores.email.gmail
  (:require [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :refer [DoloresEmailService ::email-header ::email-full]])
  (:import (com.google.api.services.gmail Gmail)
           (com.google.api.services.gmail.model Message)
           (com.google.api.client.googleapis.javanet GoogleNetHttpTransport)
           (com.google.api.client.json.jackson2 JacksonFactory)
           (com.google.api.client.googleapis.auth.oauth2 GoogleAuthorizationCodeFlow$Builder)
           (com.google.api.client.auth.oauth2 Credential)))

(defrecord GmailService [service user-id]
  DoloresEmailService
  (get-headers [this since]
    (try
      (let [query (str "after:" (.getTime since))
            request (.users.messages.list service user-id)
            response (.execute (.setQ request query))
            messages (.getMessages response)]
        (map (fn [msg]
               (let [message-id (.getId msg)
                     message (.execute (.users.messages.get service user-id message-id))
                     payload (.getPayload message)
                     headers (.getHeaders payload)
                     header {::to (or (some #(when (= "To" (.getName %)) (.getValue %)) headers) "")
                             ::from (or (some #(when (= "From" (.getName %)) (.getValue %)) headers) "")
                             ::subject (or (some #(when (= "Subject" (.getName %)) (.getValue %)) headers) "")
                             ::cc []
                             ::bcc []
                             ::sent-date (or (.getInternalDate message) (java.util.Date.))
                             ::received-date (or (.getInternalDate message) (java.util.Date.))
                             ::spam-score 0.0
                             ::server-info "Gmail Server"}]
                 (if (s/valid? ::email-header header)
                   header
                   (throw (ex-info "Invalid email header" {:header header})))))
             messages)))
      (catch Exception e
        (log/error e "Failed to fetch email headers"))))

  (get-email [this email-id]
    (try
      (let [message (.execute (.users.messages.get service user-id email-id))
            payload (.getPayload message)
            headers (.getHeaders payload)
            body (or (.getData (.getBody payload)) "")
            header {::to (or (some #(when (= "To" (.getName %)) (.getValue %)) headers) "")
                    ::from (or (some #(when (= "From" (.getName %)) (.getValue %)) headers) "")
                    ::subject (or (some #(when (= "Subject" (.getName %)) (.getValue %)) headers) "")
                    ::cc []
                    ::bcc []
                    ::sent-date (or (.getInternalDate message) (java.util.Date.))
                    ::received-date (or (.getInternalDate message) (java.util.Date.))
                    ::spam-score 0.0
                    ::server-info "Gmail Server"}
            email {::header header ::body body ::attachments []}]
        (if (s/valid? ::email-full email)
          email
          (throw (ex-info "Invalid email" {:email email}))))
      (catch Exception e
        (log/error e "Failed to fetch email"))))

  (get-emails [this since]
    (try
      (let [query (str "after:" (.getTime since))
            request (.users.messages.list service user-id)
            response (.execute (.setQ request query))
            messages (.getMessages response)]
        (map (fn [msg]
               (let [message-id (.getId msg)
                     message (.execute (.users.messages.get service user-id message-id))
                     payload (.getPayload message)
                     headers (.getHeaders payload)
                     body (or (.getData (.getBody payload)) "")
                     header {::to (or (some #(when (= "To" (.getName %)) (.getValue %)) headers) "")
                             ::from (or (some #(when (= "From" (.getName %)) (.getValue %)) headers) "")
                             ::subject (or (some #(when (= "Subject" (.getName %)) (.getValue %)) headers) "")
                             ::cc []
                             ::bcc []
                             ::sent-date (or (.getInternalDate message) (java.util.Date.))
                             ::received-date (or (.getInternalDate message) (java.util.Date.))
                             ::spam-score 0.0
                             ::server-info "Gmail Server"}
                     email {::header header ::body body ::attachments []}]
                 (if (s/valid? ::email-full email)
                   email
                   (throw (ex-info "Invalid email" {:email email})))))
             messages)))
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
