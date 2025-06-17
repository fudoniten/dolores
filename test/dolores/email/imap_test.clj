(ns dolores.email.imap-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [dolores.email.imap :as imap]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :as email])
  (:import (javax.mail.internet MimeMessage)
           (javax.mail Session Message$RecipientType)
           (javax.mail Session)
           java.time.Instant
           java.util.Date))

(defn mock-mime-message
  "Creates a mock MimeMessage for testing."
  [session & {:keys [to from subject body cc bcc] :or {body "" cc [] bcc []}}]
  (doto (MimeMessage. session)
    (.setRecipients Message$RecipientType/TO to)
    (.setRecipients Message$RecipientType/CC (str/join " " cc))
    (.setRecipients Message$RecipientType/BCC (str/join " " bcc))
    (.setFrom from)
    (.setSubject subject)
    (.setSentDate (Date.))
    ;; Simulate received date using a custom header or use sent date as a proxy
    (.addHeader "X-Received-Date" (str (Instant/now)))
    (.setContent body "text/plain")))

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

(deftest test-get-email-cc-bcc
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
