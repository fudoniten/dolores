(ns dolores.email.imap
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]
            [dolores.utils :refer [verify-args]]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :refer [DoloresEmailService] :as email])
  (:import (javax.mail Session Folder Message$RecipientType Message Multipart)
           (javax.mail.search ReceivedDateTerm ComparisonTerm)
           (java.io InputStream ByteArrayInputStream)
           (org.apache.tika Tika)
           (com.edlio.emailreplyparser EmailReplyParser)
           java.util.Date
           java.time.Instant))

(defprotocol RawEmailOperations
  "Protocol for raw email operations."
  (search-emails [this ^java.time.Instant since])
  (get-email-content [this email-id]))

(defrecord RawImapService [store]
  RawEmailOperations
  (search-emails [_ since]
    (assert (inst? since))
    (let [inbox (.getFolder store "INBOX")]
      (.open inbox Folder/READ_ONLY)
      (let [since-date (Date/from since)
            search-term (ReceivedDateTerm. ComparisonTerm/GT since-date)]
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

(let [tika (atom nil)]
  (defn get-tika []
    (if @tika @tika (swap! tika (fn [_] (Tika.))))))

;; (let [reply-parser (atom nil)]
;;   (defn get-reply-parser []
;;     (if @reply-parser @reply-parser (swap! reply-parser (fn [_] (EmailReplyParser.))))))

(defn clean-email-text
  [raw-text]
  (let [replied-text (EmailReplyParser/parseReply raw-text)]
    (-> replied-text
        ;; remove quoted lines starting with >
        (str/replace #"(?m)^>.*$" "")
        ;; Remove common signature/footer indicators
        (str/replace #"(?i)(--\s*\n.*$|^Sent from.*$)" "")
        (str/replace #"\n{3,}" "\n\n")
        (str/trim))))

(defn trace
  ([o] (trace o ""))
  ([o msg]
   (println (format "*** GOT: %s" msg))
   (clojure.pprint/pprint o)
   (println "***")))

(defn message-get-body [^Message msg]
  (let [content (.getContent msg)]
    (cond
      (string? content) content

      (instance? Multipart content)
      (let [^Multipart multipart content
            parts (for [i (range (.getCount multipart))]
                    (.getBodyPart multipart i))
            tika (get-tika)]
        (str/join "\n"
                  (map (fn [part]
                         (cond
                           (.isMimeType part "text/plain")
                           (.getContent part)

                           (.isMimeType part "text/html")
                           (.parseToString tika
                                           (-> part
                                               (.getContent)
                                               (.getBytes "UTF-8")
                                               (ByteArrayInputStream.)))

                           :else (do (log/info (format "skipping mime type: %s"
                                                       (.getContentType part)))
                                     "")))
                       parts)))

      (instance? InputStream content)
      (slurp content)

      :else (str content))))

(defn body->string [msg-body]
  (if (instance? javax.mail.internet.MimeMultipart msg-body)
    (loop [i 0
           text ""]
      (if (< i (.getCount msg-body))
        (let [part (.getBodyPart msg-body i)]
          (if (or (= (.getContentType part) "text/plain")
                  (= (.getContentType part) "text/html"))
            (recur (inc i) (str text (.getContent part)))
            (recur (inc i) text)))
        text))
    (str msg-body)))

(s/fdef parse-email
  :args (s/cat :msg (partial instance? Message))
  :ret (s/nilable ::email/email-full))
(defn parse-email
  "Converts a javax.mail.Message to the internal email format."
  [msg]
  (let [header {::email/to (str (or (first (.getRecipients msg Message$RecipientType/TO)) ""))
                ::email/from (str (or (first (.getFrom msg)) ""))
                ::email/subject (or (.getSubject msg) "")
                ::email/cc (vec (or (map #(str (.toString %)) (.getRecipients msg Message$RecipientType/CC)) []))
                ::email/bcc (vec (or (map #(str (.toString %)) (.getRecipients msg Message$RecipientType/BCC)) []))
                ::email/sent-date (or (some-> msg
                                              (.getSentDate)
                                              (.toInstant))
                                      (Instant/now))
                ::email/received-date (or (some-> msg
                                                  (.getReceivedDate)
                                                  (.toInstant))
                                          (Instant/now))
                ::email/spam-score 0.0 ;; Default spam score
                ::email/server-info "IMAP Server"}
        body (message-get-body msg)
        email {::email/header header ::email/body body ::email/attachments []}] ;; Add logic for attachments if needed
    (if (s/valid? ::email/email-full email)
      email
      (do (s/explain ::email/email-full email)
          (throw (ex-info "Invalid email" {:email email}))))))

(defrecord ImapService [raw-service]
  DoloresEmailService

  (get-email [_ email-id]
    (try
      (parse-email (get-email-content raw-service email-id))
      (catch Exception e
        (log/error e "Failed to fetch email"))))

  (get-emails [_ since]
    (try
      (map parse-email (search-emails raw-service since))
      (catch Exception e
        (log/error e "Failed to fetch emails")))))

(s/fdef connect!
  :args (s/keys* :req-un [::email/host ::email/user ::email/password])
  :ret ImapService)
(defn connect!
  [& {:keys [::host ::user ::password]}]
  (verify-args {:host host :user user :password password} [:host :user :password])
  (->ImapService (->RawImapService (-create-connection! host user password))))
