(ns dolores.email.protocol)

(defprotocol EmailService
  "Protocol for email services to fetch headers and emails."
  (get-headers [this since]
    "Fetches a list of email headers since the specified date.")
  (get-email [this email-id]
    "Fetches the full content of a specific email by ID.")
  (get-emails [this since]
    "Fetches a list of emails since the specified date."))
