(ns dolores.email.protocol
  (:require [clojure.spec.alpha :as s]))

(s/def ::client-id string?)
(s/def ::client-secret string?)
(s/def ::user-id string?)
(s/def ::credential (s/keys :req-un [::client-id ::client-secret]))
(s/def ::max-age pos-int?)

(s/def ::to string?)
(s/def ::from string?)
(s/def ::subject string?)
(s/def ::body string?)
(s/def ::instant? #(instance? java.time.Instant %))
(s/def ::sent-date ::instant?)
(s/def ::received-date ::instant?)
(s/def ::spam-score number?)
(s/def ::server-info string?)
(s/def ::message-id string?)
(s/def ::cc (s/coll-of string? :kind vector?))
(s/def ::bcc (s/coll-of string? :kind vector?))
(s/def ::attachments (s/coll-of string? :kind vector?))
(s/def ::header (s/keys :req [::to ::from ::subject ::cc ::bcc ::message-id ::sent-date ::received-date ::spam-score ::server-info]))
(s/def ::email-full (s/keys :req [::header ::body ::attachments]))

(def message-id ::message-id)

(defprotocol DoloresEmailService
  "Protocol for email services to fetch headers and emails."
  (get-email [this email-id]
    "Fetches the full content of a specific email by ID.")
  (get-emails [this since]
    "Fetches a list of emails since the specified date."))
