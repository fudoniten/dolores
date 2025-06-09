(ns dolores.email.poller)

(ns dolores.email.poller
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [dolores.utils :refer [merge-channels]])
  (:import (javax.mail Session Store Folder)
           (com.google.api.services.gmail Gmail)
           (com.google.api.services.gmail.model ListMessagesResponse)
           (com.google.api.client.googleapis.javanet GoogleNetHttpTransport)
           (com.google.api.client.json.jackson2 JacksonFactory)
           (com.google.api.client.auth.oauth2 Credential)))

(defn- fetch-imap-emails [host user password]
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

(defn- get-gmail-credentials [client-id client-secret redirect-uri]
  ;; Function to get Gmail credentials using OAuth 2.0
  (let [http-transport (GoogleNetHttpTransport/newTrustedTransport)
        json-factory (JacksonFactory/getDefaultInstance)
        flow (-> (GoogleAuthorizationCodeFlow$Builder. http-transport json-factory client-id client-secret ["https://www.googleapis.com/auth/gmail.readonly"])
                 (.setAccessType "offline")
                 (.build))
        receiver (LocalServerReceiver$Builder.)
        credential (.authorize flow "user")]
    credential))

(defn- fetch-gmail-emails [service user-id]
  ;; Function to fetch emails using Gmail API
  (let [messages (.list (.users service) user-id)
        response (.execute messages)]
    (map #(.getId %) (.getMessages response))))

(defn poll-imap [imap-config]
  ;; Poll emails from IMAP and return a channel with new emails
  (let [ch (async/chan)]
    (async/go-loop []
      (try
        (let [emails (fetch-imap-emails (:host imap-config) (:user imap-config) (:password imap-config))]
          (doseq [email emails]
            (async/>! ch email)))
        (catch Exception e
          (log/error e "Error polling IMAP emails")))
      (async/<! (async/timeout 60000)) ;; Poll every 60 seconds
      (recur))
    ch))

(defn poll-gmail [gmail-config]
  ;; Poll emails from Gmail and return a channel with new emails
  (let [ch (async/chan)]
    (async/go-loop []
      (try
        (let [emails (fetch-gmail-emails (:service gmail-config) (:user-id gmail-config))]
          (doseq [email emails]
            (async/>! ch email)))
        (catch Exception e
          (log/error e "Error polling Gmail emails")))
      (async/<! (async/timeout 60000)) ;; Poll every 60 seconds
      (recur))
    ch))

