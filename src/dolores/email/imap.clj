(ns dolores.email.imap
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]
            [dolores.utils :refer [verify-args *->> uuid-v5]]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :refer [DoloresEmailService] :as email])
  (:import (javax.mail Session Folder Message$RecipientType Message Multipart Flags$Flag)
           (javax.mail.search ReceivedDateTerm ComparisonTerm)
           (java.io InputStream)
           (com.edlio.emailreplyparser EmailReplyParser)
           (java.util Date UUID)
           java.security.MessageDigest
           java.time.Instant
           org.jsoup.Jsoup))

(def imap-namespace-uuid
  "UUID namespace to contain message IDs."
  (UUID/fromString "c0fcd99a-9593-4744-9c89-3b3598e4a73e"))

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

(defn -strip-html-tags
  [^String html]
  (.text (Jsoup/parse html)))

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

(defn message-get-body [^Message msg]
  (let [content (.getContent msg)]
    (cond
      (string? content)
      (if (.isMimeType msg "text/html")
        (-strip-html-tags content)
        content)

      (instance? Multipart content)
      (let [^Multipart multipart content
            parts (for [i (range (.getCount multipart))]
                    (.getBodyPart multipart i))]
        (str/join "\n"
                  (map (fn [part]
                         (cond
                           (.isMimeType part "text/plain")
                           (.getContent part)

                           (.isMimeType part "text/html")
                           (-> part
                               (.getContent)
                               (-strip-html-tags))

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

(defn pthru [o] (clojure.pprint/pprint o) o)

(defn gen-id [^Message msg]
  (let [addr-strs (*->> (map str) (sort) (str/join ","))
        from (addr-strs (.getFrom msg))
        to   (addr-strs (.getRecipients msg Message$RecipientType/TO))
        date (some-> msg (.getSentDate) (str))
        subj (some-> msg (.getSubject) (str))]
    (uuid-v5 imap-namespace-uuid (str from "|" to "|" date "|" subj))))

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
                ::email/message-id (or (first (.getHeader msg "Message-ID")) (gen-id msg))
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

(defn email-read?
  [^Message email]
  (not (.isSet email Flags$Flag/SEEN)))

(defrecord ImapService [raw-service]
  DoloresEmailService

  (get-email [_ email-id]
    (try
      (parse-email (get-email-content raw-service email-id))
      (catch Exception e
        (log/error e "Failed to fetch email"))))

  (get-emails [_ since]
    (try
      (->> (search-emails raw-service since)
           (filter email-read?)
           (map parse-email))
      (catch Exception e
        (log/error e "Failed to fetch emails")))))

(s/fdef connect!
  :args (s/keys* :req-un [::email/host ::email/user ::email/password])
  :ret ImapService)
(defn connect!
  [& {:keys [:host :user :password]}]
  (verify-args {:host host :user user :password password} [:host :user :password])
  (->ImapService (->RawImapService (-create-connection! host user password))))
