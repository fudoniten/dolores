(ns dolores.email.poller)

(ns dolores.email.poller
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log])
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

(defn- fetch-gmail-emails [service user-id]
  ;; Function to fetch emails using Gmail API
  (let [messages (.list (.users service) user-id)
        response (.execute messages)]
    (map #(.getId %) (.getMessages response))))

(defn poll-emails [config]
  ;; Poll emails from IMAP and Gmail, and return a channel with new emails
  (let [ch (async/chan)
        imap-config (:imap config)
        gmail-config (:gmail config)]
    (async/go-loop []
      (try
        (let [imap-emails (fetch-imap-emails (:host imap-config) (:user imap-config) (:password imap-config))
              gmail-emails (fetch-gmail-emails (:service gmail-config) (:user-id gmail-config))]
          (doseq [email (concat imap-emails gmail-emails)]
            (async/>! ch email)))
        (catch Exception e
          (log/error e "Error polling emails")))
      (async/<! (async/timeout 60000)) ;; Poll every 60 seconds
      (recur))
    ch))
