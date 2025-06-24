(ns dolores.email.gmail-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [dolores.email.gmail :as gmail]
            [clojure.spec.alpha :as s]
            [dolores.email.protocol :as email])
  (:import (com.google.api.services.gmail.model Message)))

(defn mock-gmail-message
  "Creates a mock Gmail Message for testing."
  [& {:keys [id to from subject body cc bcc] :or {body "" cc [] bcc []}}]
  (let [message (Message.)]
    (.setId message id)
    (.setPayload message
                 (doto (com.google.api.services.gmail.model.MessagePart.)
                   (.setHeaders [{:name "To" :value to}
                                 {:name "From" :value from}
                                 {:name "Subject" :value subject}
                                 {:name "Cc" :value (str/join ", " cc)}
                                 {:name "Bcc" :value (str/join ", " bcc)}])
                   (.setBody (doto (com.google.api.services.gmail.model.MessagePartBody.)
                               (.setData body)))))
    (.setInternalDate message (System/currentTimeMillis))
    message))

(defn mock-raw-gmail-operations
  "Creates a mock implementation of RawGmailOperations for testing."
  []
  (reify gmail/RawGmailOperations
    (fetch-email [_ email-id]
      (mock-gmail-message :id email-id
                          :to "to@example.com"
                          :from "from@example.com"
                          :subject "Test Subject"
                          :cc ["cc@example.com"]
                          :bcc ["bcc@example.com"]
                          :body "Test Body"))
    (fetch-emails [_ query]
      [(mock-gmail-message :id "1"
                           :to "to@example.com"
                           :from "from@example.com"
                           :subject "Test Subject"
                           :cc ["cc@example.com"]
                           :bcc ["bcc@example.com"]
                           :body "Test Body")])))

(deftest test-get-email
  (testing "Fetching full email content"
    (let [raw-ops (mock-raw-gmail-operations)
          gmail-service (gmail/->GmailService raw-ops)
          email (email/get-email gmail-service "1")]
      (is (s/valid? ::email/email-full email)))))

(deftest test-get-emails
  (testing "Fetching multiple emails"
    (let [raw-ops (mock-raw-gmail-operations)
          gmail-service (gmail/->GmailService raw-ops)
          emails (email/get-emails gmail-service (java.util.Date.))]
      (is (every? #(s/valid? ::email/email-full %) emails)))))

(run-tests)
