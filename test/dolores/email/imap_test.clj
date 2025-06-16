(ns dolores.email.imap-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [dolores.email.imap :as imap]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :as email])
  (:import (javax.mail.internet MimeMessage)
           (javax.mail Session Message$RecipientType)
           (javax.mail Session)
           java.util.Date))

(defn mock-mime-message
  "Creates a mock MimeMessage for testing."
  [session & {:keys [to from subject body] :or {body ""}}]
  (doto (MimeMessage. session)
    (.setRecipients Message$RecipientType/TO to)
    (.setFrom from)
    (.setSubject subject)
    (.setSentDate (java.util.Date.))
    (.setReceivedDate (java.util.Date.))
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
                         :subject "Test Subject"))))

(deftest test-get-headers
  (testing "Fetching email headers"
    (let [raw-ops (mock-raw-email-operations)
          imap-service (imap/->ImapService raw-ops)
          headers (email/get-headers imap-service (Date.))]
      (is (every? #(s/valid? ::email/email-full %) headers)))))

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
          emails (email/get-emails imap-service (Date.))]
      (is (every? #(s/valid? ::email/email-full %) emails)))))

(run-tests)
