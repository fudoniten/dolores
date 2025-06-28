(ns dolores.llm.ollama-test
  (:require [clojure.test :refer :all]
            [dolores.llm.ollama :as ollama]
            [dolores.utils :as utils]
            [dolores.llm :as llm]))

(deftest create-ollama-task-test
  (let [task (apply ollama/-create-ollama-task "http://localhost"
                    {::ollama/model "test-model" ::ollama/system-prompt "sys"})]
    (is (= "test-model" (:model task)))
    (is (= "sys" (:system-prompt task)))
    (is (= "http://localhost" (:base-uri task)))))

(deftest create-ollama-client-test
  (let [client (ollama/-create-ollama-client "host" 1234)]
    (is (= "http://host:1234/api" (str (:base-uri client))))))

(deftest list-models-success-test
  (let [mock-client (reify utils/IHttpClient
                      (get! [_ _ _] {:body {:models ["m1" "m2"]}}))
        client (ollama/->OllamaClient mock-client (ollama/uri "h" :port 1 :path "/api"))]
    (is (= ["m1" "m2"] (ollama/list-models client)))))

(deftest list-models-error-test
  (let [mock-client (reify utils/IHttpClient
                      (get! [_ _ _] (throw (Exception. "fail"))))
        client (ollama/->OllamaClient mock-client (ollama/uri "h" :port 1 :path "/api"))]
    (is (nil? (ollama/list-models client)))))

(deftest generate-text-success-test
  (let [mock-client (reify utils/IHttpClient
                      (post! [_ _ _] {:body {:message {:content "  hi  "}}}))
        task (ollama/->OllamaTask mock-client "uri" "m" "sp")]
    (is (= "hi" (llm/generate-text task "hello")))))

(deftest generate-text-error-test
  (let [mock-client (reify utils/IHttpClient
                      (post! [_ _ _] (throw (Exception. "fail"))))
        task (ollama/->OllamaTask mock-client "uri" "m" "sp")]
    (is (nil? (llm/generate-text task "hello")))))

(deftest create-client-test
  (let [dc (ollama/create-client "h" 1)]
    (is (instance? ollama/OllamaDoloresClient dc))))
