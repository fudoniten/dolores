(ns dolores.email.poller
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]

            [dolores.email.protocol :as email])
  (:import java.time.Instant
           java.time.Duration
           java.time.temporal.ChronoUnit))

(defn poll-emails
  "Polls emails from a DoloresEmailService at regular intervals and returns a channel with new emails."
  [service {:keys [::interval ::stop-channel ::buffer-size]
            :or   {interval     (Duration/ofMinutes 30)
                   stop-channel (async/chan)
                   buffer-size  128}}]
  (when-not (satisfies? email/DoloresEmailService service)
    (throw (ex-info "Service must implement DoloresEmailService" {})))
  (when-not (instance? Duration interval)
    (throw (ex-info "poll-interval must be a java.time.Duration" {})))
  (let [ch (async/chan (async/sliding-buffer buffer-size))
        processed-ids (atom #{})
        last-check (atom (.minus (Instant/now) 1 ChronoUnit/DAYS))
        interval-ms (.toMillis interval)]
    (async/go-loop []
      (let [now (Instant/now)
            emails (email/get-emails service @last-check)]
        (log/info "Fetched" (count emails) "emails since" @last-check)
        (doseq [email emails]
          (let [email-id (email/message-id email)]
            (when-not (contains? @processed-ids email-id)
              (swap! processed-ids conj email-id)
              (async/>! ch email))))
        (reset! last-check now)
        (let [choice (async/alt! stop-channel ([_] :stop)
                                 (async/timeout interval-ms) ([_] :continue))]
          (when (= choice :continue)
            (recur)))))
    {::emails ch ::stop stop-channel}))
