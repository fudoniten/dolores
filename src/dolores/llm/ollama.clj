(ns dolores.llm.ollama
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]
            [cheshire.core :as json]
            [dolores.llm :as llm]
            [dolores.utils :refer [uri add-path create-http-client get! post!]]))

(defprotocol IOllamaClient
  "Protocol defining operations for Ollama LLM client."
  (list-models [self]
    "List available models from Ollama server.")
  (create-task [self model system-prompt]
    "Create a task for model with system prompt."))

(defrecord OllamaTask [http-client base-uri model system-prompt]
  llm/LLMClient
  (generate-text [_ content errors]
    (try
      (let [response
            (post! http-client
                   (str (add-path base-uri "chat"))
                   {:body (json/generate-string
                           {:model model
                            :messages [{:role "system"
                                        :content (str/join "\n"
                                                           ["Process the user content according to the following prompt:"
                                                            ""
                                                            system-prompt
                                                            ""
                                                            "Fix any errors from previous attempts, specified by \"generation-errors\"."])}
                                       {:role "user" :content content :generation-errors errors}]
                            :stream false})
                    :headers {"Content-Type" "application/json"}
                    :as :json})]
        (-> response
            (get-in [:body :message :content])
            (str/trim)))
      (catch Exception e
        (log/error e "Failed to generate text with ollama")
        nil))))

(defn ^:private -create-ollama-task
  "Create a new OllamaTask with base URI, model, and system prompt."
  [base-uri &
   {:keys [::model ::system-prompt]}]
  (->OllamaTask (create-http-client) base-uri model system-prompt))

(defrecord OllamaClient [http-client base-uri]
  IOllamaClient
  (list-models [_]
    (try
      (let [response
            (get! http-client
                  (str (add-path base-uri "tags"))
                  {:as :json})]
        (map :model (get-in response [:body :models])))
      (catch Exception e
        (log/error e "Failed to fetch list of ollama models")
        nil)))

  (create-task [_ model system-prompt]
    (->OllamaTask http-client base-uri model system-prompt)))

(defn ^:private -create-ollama-client
  "Create an OllamaClient given host and port."
  [host port]
  (->OllamaClient (create-http-client) (uri host :port port :path "/api")))

(defrecord OllamaDoloresClient
    [prioritizer summarizer bulk-summarizer highlighter]

  llm/LLMDoloresClient

  (prioritize-email [_ email]
    (llm/generate-text prioritizer {:email email}))

  (summarize-email [_ email]
    (llm/generate-text summarizer {:email email}))

  (summarize-emails [_ emails]
    (llm/generate-text bulk-summarizer {:email emails}))

  (highlight-emails [_ emails]
    (llm/generate-text highlighter {:email emails})))

(defn generate-text-with-retries
  "Generate text with retries, attempting to parse and validate the result.
   Throws an exception if it fails within the specified max attempts."
  [llm-client content validation-fn max-attempts]
  (loop [attempt 1
         errors nil]
    (if (> attempt max-attempts)
      (throw (ex-info "Failed to generate valid text within max attempts" {:attempts max-attempts}))
      (let [result (llm/generate-text llm-client content errors)]
        (try
          (let [parsed-result (json/parse-string result true)]
            (if (validation-fn parsed-result)
              parsed-result
              (recur (inc attempt) "Validation failed")))
          (catch Exception e
            (recur (inc attempt) (str "Parsing failed: " (.getMessage e)))))))))


(defn create-client
  "Create an OllamaDoloresClient connecting to the Ollama LLM backend.
   Requires keyword prompts ::prioritize-prompt, ::summarize-prompt, ::bulk-summarize-prompt, ::highlight-prompt."
  [host port
   & {:keys [::prioritize-prompt ::summarize-prompt ::bulk-summarize-prompt ::highlight-prompt]}]
  (let [client (-create-ollama-client host port)]
    (->OllamaDoloresClient (-create-ollama-task client prioritize-prompt)
                           (-create-ollama-task client summarize-prompt)
                           (-create-ollama-task client bulk-summarize-prompt)
                           (-create-ollama-task client highlight-prompt))))
