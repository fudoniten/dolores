(ns dolores.utils
  (:require [clojure.core.async :as async]))

(defn merge-channels [channels]
  ;; Merge multiple channels into a single channel
  (let [merged-ch (async/chan)]
    (doseq [ch channels]
      (async/go-loop []
        (when-let [email (async/<! ch)]
          (async/>! merged-ch email)
          (recur))))
    merged-ch))
(ns dolores.utils)

(defn verify-args
  "Verifies that all required keys are present in the map."
  [m required-keys]
  (doseq [k required-keys]
    (when (nil? (get m k))
      (throw (ex-info (str "Missing required argument: " k) {:missing-key k})))))
