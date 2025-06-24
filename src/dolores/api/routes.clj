(ns dolores.api.routes
  (:require [compojure.core :refer [routes GET]]
            [dolores.api.handler :refer [handle-summary-request handle-important-request]]
            [compojure.route :as route]
            [ring.util.response :as response]))

(defn define-routes []
  ;; Function to define API routes
  (routes
    (GET "/emails/summary" [] handle-summary-request)
    (GET "/emails/important" [] handle-important-request)
    (route/not-found "Not Found")))
