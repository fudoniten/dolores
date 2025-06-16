(ns dolores.email.poller
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log])
  (:import (javax.mail Session Store Folder)
           (com.google.api.services.gmail Gmail)
           (com.google.api.services.gmail.model ListMessagesResponse)
           (com.google.api.client.googleapis.javanet GoogleNetHttpTransport)
           (com.google.api.client.json.jackson2 JacksonFactory)
           (com.google.api.client.googleapis.auth.oauth2 GoogleAuthorizationCodeFlow$Builder)
           (com.google.api.client.auth.oauth2 Credential)))

(defn- fetch-imap-emails
  "Fetches emails using IMAP protocol from the specified host with given user credentials."
  [host user password]
  ;; Function to fetch emails using IMAP
  (let [props (System/getProperties)]
    (.put props "mail.store.protocol" "imaps")
    (let [session (Session/getDefaultInstance props nil)
          store (.getStore session "imaps")]
      (.connect store host user password)
      (let [inbox (.getFolder store "INBOX")]
        (.open inbox Folder/READ_ONLY)
        (let [messages (.getMessages inbox)]
          (map #(str (.getSubject %)) messages))))))


(defn- fetch-gmail-emails
  "Fetches emails using the Gmail API for the specified user ID."
  [service user-id]
  ;; Function to fetch emails using Gmail API
  (let [messages (.list (.users service) user-id)
        response (.execute messages)]
    (map #(.getId %) (.getMessages response))))

(defn poll-imap
  "Polls emails from an IMAP server at regular intervals and returns a channel with new emails."
  [{:keys [::imap-host ::imap-user ::imap-password]} {:keys [::poll-interval]}]
  ;; Poll emails from IMAP and return a channel with new emails
  (let [ch (async/chan)
        processed-ids (atom #{})]
    (async/go-loop []
      (try
        (let [emails (fetch-imap-emails imap-host imap-user imap-password)]
          (doseq [email emails]
            (let [email-id (get-email-id email)] ;; Assume get-email-id extracts the ID
              (when-not (contains? @processed-ids email-id)
                (swap! processed-ids conj email-id)
                (async/>! ch email)))))
        (catch Exception e
          (log/error e "Error polling IMAP emails")))
      (async/<! (async/timeout poll-interval)) ;; Poll at configured interval
      (recur))
    ch))

(defn poll-gmail
  "Polls emails from Gmail at regular intervals and returns a channel with new emails."
  [{:keys [::gmail-service ::gmail-user-id]} {:keys [::poll-interval]}]
  ;; Poll emails from Gmail and return a channel with new emails
  (let [ch (async/chan)
        processed-ids (atom #{})]
    (async/go-loop []
      (try
        (let [emails (fetch-gmail-emails gmail-service gmail-user-id)]
          (doseq [email emails]
            (let [email-id (get-email-id email)] ;; Assume get-email-id extracts the ID
              (when-not (contains? @processed-ids email-id)
                (swap! processed-ids conj email-id)
                (async/>! ch email)))))
        (catch Exception e
          (log/error e "Error polling Gmail emails")))
      (async/<! (async/timeout poll-interval)) ;; Poll at configured interval
      (recur))
    ch))

