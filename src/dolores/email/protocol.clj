(ns dolores.email.protocol
  (:require [clojure.spec.alpha :as s]))

(s/def ::to string?)
(s/def ::from string?)
(s/def ::subject string?)
(s/def ::body string?)
(s/def ::sent-date inst?)
(s/def ::received-date inst?)
(s/def ::spam-score number?)
(s/def ::server-info string?)
(s/def ::cc (s/coll-of string? :kind vector?))
(s/def ::bcc (s/coll-of string? :kind vector?))
(s/def ::attachments (s/coll-of string? :kind vector?))
(s/def ::header (s/keys :req [::to ::from ::subject ::cc ::bcc ::sent-date ::received-date ::spam-score ::server-info]))
(s/def ::email-header (s/keys :req [::header]))
(s/def ::email-full (s/keys :req [::header ::body ::attachments]))

(defprotocol DoloresEmailService
  "Protocol for email services to fetch headers and emails."
  (get-email [this email-id]
    "Fetches the full content of a specific email by ID.")
  (get-emails [this since]
    "Fetches a list of emails since the specified date."))

(defprotocol DoloresEmailService
  "Protocol for email services to fetch headers and emails."
  (get-email [this email-id]
    "Fetches the full content of a specific email by ID.")
  (get-emails [this since]
    "Fetches a list of emails since the specified date."))
