(ns dolores.email.imap-test
  (:require [clojure.test :refer :all]
            [dolores.email.imap :refer :all]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :as email]))

(defn mock-email
  "Creates a mock email message for testing."
  [to from subject]
  {:to to
   :from from
   :subject subject
   :cc []
   :bcc []
   :sent-date (java.util.Date.)
   :received-date (java.util.Date.)
   :spam-score 0.0
   :server-info "Mock Server"})

(defn mock-raw-email-operations
  "Creates a mock implementation of RawEmailOperations for testing."
  []
  (reify RawEmailOperations
    (search-emails [_ since]
      [(mock-email "to@example.com" "from@example.com" "Test Subject")])
    (get-email-content [_ email-id]
      (mock-email "to@example.com" "from@example.com" "Test Subject"))))

(deftest test-get-headers
  (testing "Fetching email headers"
    (let [raw-ops (mock-raw-email-operations)
          imap-service (->IMAPServiceWrapper raw-ops)
          headers (get-headers imap-service (java.util.Date.))]
      (is (every? #(s/valid? ::email/email-header %) headers)))))

(deftest test-get-email
  (testing "Fetching full email content"
    (let [raw-ops (mock-raw-email-operations)
          imap-service (->IMAPServiceWrapper raw-ops)
          email (get-email imap-service "mock-id")]
      (is (s/valid? ::email/email-full email)))))

(deftest test-get-emails
  (testing "Fetching multiple emails"
    (let [raw-ops (mock-raw-email-operations)
          imap-service (->IMAPServiceWrapper raw-ops)
          emails (get-emails imap-service (java.util.Date.))]
      (is (every? #(s/valid? ::email/email-full %) emails)))))
