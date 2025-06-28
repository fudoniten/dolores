(ns dolores.time-window
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen])
  (:import (java.time Duration Instant)))

(defn compare-by-key
  "Returns a comparator function that compares maps by the value at key k."
  [k]
  (fn [a b]
    (compare (get a k) (get b k))))

(defn truncate
  "Filters items with key k values >= cutoff and returns them as a sorted set."
  [items k cutoff]
  (into (sorted-set-by (compare-by-key k))
        (filter (fn [el] (>= (compare (get el k) cutoff) 0))
                items)))

(defn now-fn
  "Default time function returning current Instant."
  []
  (Instant/now))

(defprotocol ITimeWindow
  (insert       [self i])
  (insert-items [self is])
  (trim         [self])
  (entries      [self]))

(defn get-cutoff
  "Returns the cutoff Instant by subtracting window-size from time-fn."
  [window-size time-fn]
  (.minus (time-fn) window-size))

(defrecord TimeWindow [items k window-size time-fn]
  ITimeWindow
  (insert [_ i]
    (->TimeWindow (truncate (conj items i) k (get-cutoff window-size time-fn))
                  k window-size time-fn))
  (insert-items [_ is]
    (->TimeWindow (truncate (concat items is) k (get-cutoff window-size time-fn))
                  k window-size time-fn))
  (trim [_]
    (->TimeWindow (truncate items k (get-cutoff window-size time-fn))
                  k window-size time-fn))
  (entries [_] (vec (truncate items k (get-cutoff window-size time-fn)))))

(defn time-window
  "Creates a TimeWindow that keeps entries within window-size Duration based on timestamp key k.
   window-size must be a java.time.Duration."
  [k window-size
   & {:keys [time-fn]
      :or   {time-fn now-fn}}]
  (assert (instance? Duration window-size))
  (->TimeWindow [] k window-size time-fn))

(defn random-instant-around-window
  "For testing: given a duration, return a timestamp within (duration * 1.5) of now."
  [^Duration duration]
  (let [now (Instant/now)
        millis (.toMillis duration)
        range-start (.minusMillis now (long (* millis 1.5)))
        total-range (long (* millis 1.5))
        offset (rand-int (inc total-range))]
    (.plusMillis range-start offset)))

(defn time-window-of
  "Returns a spec for a time window whose entries conform to entry-spec."
  [entry-spec order-key]
  (s/with-gen
    ;; Spec valiator
    (s/and (partial satisfies? ITimeWindow)
           (fn [window] (every? (fn [el]
                                 (and (s/valid? entry-spec el)
                                      (contains? el order-key)))
                               (entries window))))
    ;; Generator
    (fn []
      (let [duration (Duration/ofHours 12)
            entries (repeatedly (+ 1 (rand-int 15))
                                (fn []
                                  (-> (gen/generate (s/gen entry-spec))
                                      (assoc order-key (random-instant-around-window duration)))))]
        (->TimeWindow entries order-key duration now-fn)))))
