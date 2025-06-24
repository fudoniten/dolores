(ns dolores.email.protocol-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :as email])
  (:import java.time.Instant))

(def valid-email
  {::email/header {::email/to "to@example.com"
                   ::email/from "from@example.com"
                   ::email/subject "Test Subject"
                   ::email/cc ["cc@example.com"]
                   ::email/bcc ["bcc@example.com"]
                   ::email/sent-date (Instant/now)
                   ::email/received-date (Instant/now)
                   ::email/spam-score 0.0
                   ::email/server-info "Test Server"}
   ::email/body "This is a test email body."
   ::email/attachments []})

(def invalid-email
  {::email/header {::email/to nil
                   ::email/from "from@example.com"
                   ::email/subject "Test Subject"
                   ::email/cc ["cc@example.com"]
                   ::email/bcc ["bcc@example.com"]
                   ::email/sent-date (Instant/now)
                   ::email/received-date (Instant/now)
                   ::email/spam-score 0.0
                   ::email/server-info "Test Server"}
   ::email/body "This is a test email body."
   ::email/attachments []})

(deftest test-valid-email
  (testing "Valid email should conform to the spec"
    (is (s/valid? ::email/email-full valid-email))
    (s/explain ::email/email-full valid-email)))

(deftest test-invalid-email
  (testing "Invalid email should not conform to the spec"
    (is (not (s/valid? ::email/email-full invalid-email)))
    (s/explain ::email/email-full invalid-email)))

(run-tests)
