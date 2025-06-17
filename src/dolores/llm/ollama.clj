(ns dolores.llm.ollama
  (:require [clojure.tools.logging :as log]
            [clj-http.client :as http]))

(defprotocol LLM
  "Protocol for interacting with an LLM server."
  (generate-text [this prompt]
    "Generates text based on the given prompt."))

(defrecord OllamaClient [base-url]
  LLM
  (generate-text [this prompt]
    (try
      (let [response (http/post (str base-url "/generate")
                                {:body (json/write-str {:prompt prompt})
                                 :headers {"Content-Type" "application/json"}
                                 :as :json})]
        (get-in response [:body :text]))
      (catch Exception e
        (log/error e "Failed to generate text with Ollama")))))

(defn create-ollama-client
  "Creates an Ollama client for interacting with the LLM server."
  [base-url]
  (->OllamaClient base-url))
