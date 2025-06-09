(ns dolores.llm)

(defprotocol LLM
  "Protocol for interacting with Large Language Models (LLMs)."

  (generate-text [this prompt]
    "Generate text based on the given prompt."))

(defrecord GenericLLM []
  LLM
  (generate-text [this prompt]
    ;; Placeholder implementation
    (str "Generated text for prompt: " prompt)))
