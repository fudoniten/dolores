(ns dolores.api.routes
  (:require [compojure.core :refer [routes GET]]
            [compojure.route :as route]
            [ring.util.response :as response]))

(defn define-routes []
  ;; Function to define API routes
  (routes
    (GET "/emails/summary" [] (response/response "Quick summary of recent emails"))
    (GET "/emails/important" [] (response/response "Summary of emails to read or reply to"))
    (route/not-found "Not Found")))
