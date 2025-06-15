(ns dolores.email.protocol
  (:require [clojure.spec.alpha :as s]))

(s/def ::to string?)
(s/def ::from string?)
(s/def ::subject string?)
(s/def ::body string?)
(s/def ::header (s/keys :req-un [::to ::from ::subject]))
(s/def ::email (s/keys :req-un [::header ::body]))

(defprotocol DoloresEmailService
  "Protocol for email services to fetch headers and emails."
  (get-headers [this since]
    "Fetches a list of email headers since the specified date.")
  (get-email [this email-id]
    "Fetches the full content of a specific email by ID.")
  (get-emails [this since]
    "Fetches a list of emails since the specified date."))

(defprotocol DoloresEmailService
  "Protocol for email services to fetch headers and emails."
  (get-headers [this since]
    "Fetches a list of email headers since the specified date.")
  (get-email [this email-id]
    "Fetches the full content of a specific email by ID.")
  (get-emails [this since]
    "Fetches a list of emails since the specified date."))
