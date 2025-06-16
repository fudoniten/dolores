(ns dolores.email.protocol-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :as email]))

(def valid-email
  {:header {:to "to@example.com"
            :from "from@example.com"
            :subject "Test Subject"
            :cc ["cc@example.com"]
            :bcc ["bcc@example.com"]
            :sent-date (java.util.Date.)
            :received-date (java.util.Date.)
            :spam-score 0.0
            :server-info "Test Server"}
   :body "This is a test email body."
   :attachments []})

(def invalid-email
  {:header {:to nil
            :from "from@example.com"
            :subject "Test Subject"
            :cc ["cc@example.com"]
            :bcc ["bcc@example.com"]
            :sent-date (java.util.Date.)
            :received-date (java.util.Date.)
            :spam-score 0.0
            :server-info "Test Server"}
   :body "This is a test email body."
   :attachments []})

(deftest test-valid-email
  (testing "Valid email should conform to the spec"
    (is (s/valid? ::email/email-full valid-email))))

(deftest test-invalid-email
  (testing "Invalid email should not conform to the spec"
    (is (not (s/valid? ::email/email-full invalid-email)))
    (s/explain ::email/email-full invalid-email)))
