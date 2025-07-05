(ns dolores.state
  (:require [clojure.spec.alpha :as s]

            [dolores.email.protocol :as email]
            [dolores.llm :as llm]
            [dolores.time-window :as tw]))

(s/def ::summary string?)
(s/def ::action-required boolean?)
(s/def ::recommended-response string?)

(s/def ::action-items (s/coll-of string?))
(s/def ::critical-info (s/coll-of string?))

(s/def ::summary string?)
(s/def ::tag string?)
(s/def ::tags (s/map-of ::tag integer?))

(s/def ::priority #{:high :medium :low})
(s/def ::priority-reason string?)

(s/def ::email-summaries
  (s/map-of ::email/message-id
            (s/keys :req [::summary
                          ::action-required
                          ::recommended-response])))

(s/def ::email-bulk-summary
  (s/keys :req [::summary
                ::tags
                ::critical-info]))

(s/def ::email-priority
  (s/keys :req [::priority ::priority-reason]))

(s/def ::email-hilights
  (s/keys :req [::action-items
                ::critical-info]))

(s/def ::current-email
  (tw/time-window-of ::email/email-full ::email/received-date))

(s/def ::email-state
  (s/keys :req [::email-summaries
                ::email-bulk-summary
                ::email-hilights
                ::current-email]))

(defprotocol IUserEmailState
  (add-email [self email])
  (get-summary [self message-id])
  (get-bulk-summary [self])
  (get-hilights [self])
  (summarize [self dolores-llm-client]))

(defn create-user-state [window-size]
  (atom
   {::email-summaries {}
    ::email-bulk-summary nil
    ::email-hilights nil
    ::current-email (atom (tw/time-window ::email/received-date window-size))}
   :validator (partial s/valid? ::email-state)))

(defrecord UserEmailState [state dolores-client]
  IUserEmailState

  (add-email [_ email]
    (swap! state
           update ::current-email
           (fn [win] (tw/insert win
                               (assoc email ::email/priority
                                      (llm/prioritize-email dolores-client
                                                            email))))))

  (get-summary [_ message-id]
    (get-in @state [::email-summaries message-id]))

  (get-bulk-summary [_]
    (get @state ::email-bulk-summary))

  (get-hilights [_]
    (get @state ::email-hilights))

  (summarize [_ summarizer]
    ))
