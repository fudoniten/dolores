(ns dolores.utils
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [clj-http.client :as http])
  (:import java.net.URI
           (java.util UUID Arrays)
           java.security.MessageDigest
           java.nio.ByteBuffer))

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

(defprotocol IHttpClient
  (get!  [self url req])
  (post! [self url req]))

(defrecord HttpClient []
  IHttpClient
  (get! [_ url req]
    (http/get url req))
  (post! [_ url req]
    (http/post url req)))

(defn create-http-client []
  (->HttpClient))

(defmacro *-> [& fs]
  (let [init (gensym)]
    `(fn [~init] (-> ~init ~@fs))))

(defmacro *->> [& fs]
  (let [init (gensym)]
    `(fn [~init] (->> ~init ~@fs))))

(defn uuid-v5
  "Generate a UUIDv5 from a namespace UUID and a name string."
  [^UUID namespace ^String name]
  (let [ns-bytes (ByteBuffer/allocate 16)
        _ (.putLong ns-bytes (.getMostSignificantBits namespace))
        _ (.putLong ns-bytes (.getLeastSignificantBits namespace))
        name-bytes (.getBytes name "UTF-8")
        input (byte-array (+ 16 (count name-bytes)))
        _ (System/arraycopy (.array ns-bytes) 0 input 0 16)
        _ (System/arraycopy name-bytes 0 input 16 (count name-bytes))
        sha1 (.digest (doto (MessageDigest/getInstance "SHA-1")
                        (.update input)))
        uuid-bytes (Arrays/copyOf sha1 16)]
    ;; Set version to 5
    (aset uuid-bytes 6 (unchecked-byte (bit-or 0x50 (bit-and (aget uuid-bytes 6) 0x0f))))
    ;; Set variant to IETF
    (aset uuid-bytes 8 (unchecked-byte (bit-or 0x80 (bit-and (aget uuid-bytes 8) 0x3f))))
    (let [bb (ByteBuffer/wrap uuid-bytes)]
      (UUID. (.getLong bb) (.getLong bb)))))
