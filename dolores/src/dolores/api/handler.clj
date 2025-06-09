(ns dolores.api.handler
  (:require [ring.util.response :as response]))

(defn get-email-summaries
  "Handles requests to get email summaries."
  [request]
  ;; TODO: Implement API logic to return email summaries
  (response/response "Email summaries will be here."))
