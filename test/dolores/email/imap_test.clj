(ns dolores.email.imap-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [dolores.email.imap :as imap]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :as email])
  (:import (javax.mail.internet MimeMessage MimeBodyPart MimeMultipart)
           (javax.mail Session Message$RecipientType)
           (javax.mail Session)
           java.time.Instant
           java.util.Date))

(defn mock-mime-message
  "Creates a mock MimeMessage for testing."
  [session & {:keys [to from subject body cc bcc mime-type]
              :or {body "" cc [] bcc [] mime-type "text/plain"}}]
  (doto (MimeMessage. session)
    (.setRecipients Message$RecipientType/TO to)
    (.setRecipients Message$RecipientType/CC (str/join " " cc))
    (.setRecipients Message$RecipientType/BCC (str/join " " bcc))
    (.setFrom from)
    (.setSubject subject)
    (.setSentDate (Date.))
    (.addHeader "X-Received-Date" (str (Instant/now)))
    (.setContent body mime-type)
    (.saveChanges)))

(defn mock-mime-multipart-message
  "Creates a mock MimeMessage for testing."
  [session & {:keys [to from subject body cc bcc mime-type]
              :or {body "" cc [] bcc [] mime-type "text/plain"}}]
  (let [part (doto (MimeBodyPart.)
               (.setContent body (str mime-type "; charset=UTF-8")))
        multipart (doto (MimeMultipart.)
                    (.addBodyPart part))]
    (doto (MimeMessage. session)
      (.setRecipients Message$RecipientType/TO to)
      (.setRecipients Message$RecipientType/CC (str/join " " cc))
      (.setRecipients Message$RecipientType/BCC (str/join " " bcc))
      (.setFrom from)
      (.setSubject subject)
      (.setSentDate (Date.))
      (.addHeader "X-Received-Date" (str (Instant/now)))
      (.setContent multipart)
      (.saveChanges))))

(defn mock-raw-email-operations
  "Creates a mock implementation of RawEmailOperations for testing."
  []
  (reify imap/RawEmailOperations
    (search-emails [_ since]
      [(mock-mime-message (Session/getDefaultInstance (System/getProperties))
                          :to "to@example.com"
                          :from "from@example.com"
                          :subject "Test Subject")])
    (get-email-content [_ email-id]
      (mock-mime-message (Session/getDefaultInstance (System/getProperties))
                         :to "to@example.com"
                         :from "from@example.com"
                         :subject "Test Subject"
                         :cc ["cc@example.com"]
                         :bcc ["bcc@example.com"]
                         :body "Test Body"))))

(deftest test-clean-email-text
  (testing "Cleaning email text"
    (let [raw-text "Hello,\n\nThis is a test email.\n\n> Quoted text\n\n-- \nSignature\n\nSent from my iPhone"
          expected "Hello,\n\nThis is a test email."
          cleaned-text (imap/clean-email-text raw-text)]
      (is (= expected cleaned-text)))))

(deftest test-message-get-body-text-plain
  (testing "Extracting body from message"
    (let [session (Session/getDefaultInstance (System/getProperties))
          message (mock-mime-message session
                                     :to "to@example.com"
                                     :from "from@example.com"
                                     :subject "Test Subject"
                                     :body "This is the body of the email.")]
      (is (= "This is the body of the email." (imap/message-get-body message))))))

(deftest test-message-get-body-multipart
  (testing "Extracting body from multipart message"
    (let [session (Session/getDefaultInstance (System/getProperties))
          message (doto (MimeMessage. session)
                    (.setRecipients Message$RecipientType/TO "to@example.com")
                    (.setFrom "from@example.com")
                    (.setSubject "Test Subject")
                    (.setSentDate (Date.))
                    (.addHeader "X-Received-Date" (str (Instant/now)))
                    (.setContent (doto (javax.mail.internet.MimeMultipart.)
                                   (.addBodyPart (doto (javax.mail.internet.MimeBodyPart.)
                                                   (.setText "This is the plain text part of the email.")))
                                   (.addBodyPart (doto (javax.mail.internet.MimeBodyPart.)
                                                   (.setContent "<html><body>This is the <b>HTML</b> part of the email.</body></html>" "text/html; charset=UTF-8")))))
                    (.saveChanges))]
      (is (= "This is the plain text part of the email.\nThis is the HTML part of the email."
             (str/trim (imap/message-get-body message)))))))

(deftest test-message-get-body-html
  (testing "Extracting body from HTML multipart message"
    (let [session (Session/getDefaultInstance (System/getProperties))
          message (-> (mock-mime-multipart-message session
                                                   :to "to@example.com"
                                                   :from "from@example.com"
                                                   :subject "Test Subject"
                                                   :body "<html><body>This is the <b>HTML</b> body of the email.</body></html>"
                                                   :mime-type "text/html"))]
      (is (= "This is the HTML body of the email." (str/trim (imap/message-get-body message)))))
  (testing "Extracting body from HTML message"
    (let [session (Session/getDefaultInstance (System/getProperties))
          message (-> (mock-mime-message session
                                         :to "to@example.com"
                                         :from "from@example.com"
                                         :subject "Test Subject"
                                         :body "<html><body>This is the <b>HTML</b> body of the email.</body></html>"
                                         :mime-type "text/html"))]
      (is (= "This is the HTML body of the email." (str/trim (imap/message-get-body message))))))))

(deftest test-email-cc-and-bcc
  (testing "Fetching email with CC and BCC"
    (let [raw-ops (mock-raw-email-operations)
          imap-service (imap/->ImapService raw-ops)
          email (email/get-email imap-service "mock-id")]
      (is (= ["cc@example.com"] (get-in email [::email/header ::email/cc])))
      (is (= ["bcc@example.com"] (get-in email [::email/header ::email/bcc]))))))


(deftest test-get-email
  (testing "Fetching full email content"
    (let [raw-ops (mock-raw-email-operations)
          imap-service (imap/->ImapService raw-ops)
          email (email/get-email imap-service "mock-id")]
      (is (s/valid? ::email/email-full email)))))

(deftest test-get-emails
  (testing "Fetching multiple emails"
    (let [raw-ops (mock-raw-email-operations)
          imap-service (imap/->ImapService raw-ops)
          emails (email/get-emails imap-service (Instant/now))]
      (is (every? #(s/valid? ::email/email-full %) emails)))))

(run-tests)
