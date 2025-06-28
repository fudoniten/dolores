(ns dolores.api.routes
  (:require [cheshire.core :as json]
            [taoensso.timbre :as log]
            [reitit.ring :as ring]))

(defn- decode-response-body
  "Middleware to parse the response body as JSON"
  [handler]
  (fn [{:keys [body] :as req}]
    (if body
      (let [body-str (slurp body)]
        (handler (-> req
                     (assoc :payload (if (= body-str "")
                                       {}
                                       (json/parse-string body-str keyword)))
                     (assoc :body-str body-str))))
      (handler (-> req (assoc :body-str ""))))))

(defn- encode-request-body
  "Middleware to serialize the response body as JSON"
  [handler]
  (fn [req]
    (let [resp (handler req)]
      (assoc resp :body (json/generate-string (:body resp))))))

(defn- keywordize-headers
  "Middleware to convert header names to keywords"
  [handler]
  (fn [req]
    (handler (update req :headers
                     (fn [headers] (update-keys headers keyword))))))

(defn log-events
  [handler]
  (fn [req]
    (log/debug {:event   "receiving request"
                :request req})
    (try
     (let [result (handler req)]
       (log/debug {:event    "sending-response"
                   :status   (:status result)
                   :response result})
       result)
     (catch Exception e
       (log/warn {:event "request-failed"
                  :error e
                  :uri (:uri req)
                  :method (-> req :request-method name)})
       (throw e)))))

(defn define-routes [dolores-data]
  (ring/router [["/api"
                 ["/v1" {:middleware [keywordize-headers
                                      encode-request-body
                                      decode-response-body
                                      log-events]}
                  [["/:user-id"
                    [["/email"
                      ["/summaries"]
                      ["/hilights"]
                      ["/summary/:message-id"]
                      ["/hilights/:message-id"]]]]]]]]))
