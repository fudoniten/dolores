(ns dolores.utils
  (:require [clojure.core.async :as async]
            [clojure.string :as str])
  (:import java.net.URI))

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

(defn join-paths [& segments]
  (->> segments
       (map (fn [seg] (str/replace seg #"^/|/$" "")))
       (remove str/blank?)
       (str/join "/")
       (str "/")))

(defprotocol IUri
  (set-path [url path])
  (add-path [url path]))

(defrecord Uri [host port scheme userinfo path query fragment]
  Object
  (toString [_]
    (let [uri (URI. scheme userinfo host port path query fragment)]
      (.toString uri)))
  IUri
  (set-path [_ new-path]
    (->Uri host port scheme userinfo new-path query fragment))
  (add-path [_ extra-path]
    (->Uri host port scheme userinfo (join-paths path extra-path) query fragment)))

(defn uri [host & {:keys [port scheme path query fragment userinfo]
                   :or   {port     80
                          scheme   "http"
                          path     "/"}}]
  (->Uri host port scheme userinfo path query fragment))
