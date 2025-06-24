(ns dolores.api.handler
  (:require [ring.util.response :as response]))

(defn handle-summary-request [request]
  ;; Handler for quick summary of recent emails
  (response/response "Quick summary of recent emails"))

(defn handle-important-request [request]
  ;; Handler for summary of emails to read or reply to
  (response/response "Summary of emails to read or reply to"))

(defn handle-request [request]
  ;; Function to handle API requests
  )
