(ns dolores.llm)

(defprotocol LLMClient
  "Protocol for interacting with Large Language Models (LLMs)."

  (generate-text [this content errors]
    "Generate text based on the given prompt."))

(defprotocol LLMDoloresClient
  "Protocol for interacting with LLMs, with prepared prompts for specific use-cases."

  (prioritize-email [this email])
  (summarize-email [this email])
  (summarize-emails [this emails])
  (highlight-emails [this summaries]))
