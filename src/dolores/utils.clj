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
