(ns dolores.llm.ollama
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]
            [cheshire.core :as json]
            [dolores.llm :as llm]
            [dolores.utils :refer [uri add-path]]
            [clj-http.client :as http]))

(defn pthru
  "Pretty prints the given object for debugging."
  [o]
  (clojure.pprint/pprint o))

(defprotocol IOllamaClient
  (list-models [self])
  (create-task [self model system-prompt]))

(defrecord OllamaTask [base-uri model system-prompt]
  llm/LLMClient
  (generate-text [_ content]
    (try
      (let [response
            (http/post (str (add-path base-uri "chat"))
                       {:body (json/generate-string
                               {:model model
                                :messages [{:role "system" :content system-prompt}
                                           {:role "user" :content content}]
                                :stream false})
                        :headers {"Content-Type" "application/json"}
                        :as :json})]
        (-> response
            (get-in [:body :message :content])
            (str/trim)))
      (catch Exception e
        (log/error e "Failed to generate text with ollama")))))

(defn -create-ollama-task
  "Create a new OllamaTask with base URI, model, and system prompt."
  [base-uri &
   {:keys [::model ::system-prompt]}]
  (->OllamaTask base-uri model system-prompt))

(defrecord OllamaClient [base-uri]
  IOllamaClient
  (list-models [_]
    (try
      (let [response
            (http/get (str (add-path base-uri "tags"))
                      :as :json)]
        (get-in response [:body :models]))
      (catch Exception e
        (log/error e "Failed to fetch list of ollama models"))))

  (create-task [_ model system-prompt]
    (-create-ollama-task base-uri
                         ::model model
                         ::system-prompt system-prompt)))

(defn -create-ollama-client
  "Create an OllamaClient given host and port."
  [host port]
  (->OllamaClient (uri host :port port :path "/api")))

(defrecord OllamaDoloresClient
    [prioritizer summarizer bulk-summarizer highlighter]

  llm/LLMDoloresClient

  (prioritize-email [_ email]
    (llm/generate-text prioritizer email))

  (summarize-email [_ email]
    (llm/generate-text summarizer email))

  (summarize-emails [_ emails]
    (llm/generate-text bulk-summarizer emails))

  (highlight-emails [_ emails]
    (llm/generate-text highlighter emails)))


(defn create-client
  "Create an OllamaDoloresClient connecting to the Ollama LLM backend.
   host: server host, port: server port.
   Supports keyword prompts ::prioritize-prompt, ::summarize-prompt, ::bulk-summarize-prompt, ::highlight-prompt."
  [host port
   & {:keys [::prioritize-prompt ::summarize-prompt ::bulk-summarize-prompt ::highlight-prompt]}]
  (let [client (-create-ollama-client host port)]
    (->OllamaDoloresClient (-create-ollama-task client prioritize-prompt)
                           (-create-ollama-task client summarize-prompt)
                           (-create-ollama-task client bulk-summarize-prompt)
                           (-create-ollama-task client highlight-prompt))))
