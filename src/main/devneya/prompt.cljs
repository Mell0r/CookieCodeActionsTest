(ns devneya.prompt
  (:require [devneya.codeGeneration :as gen]
            [devneya.exec :as exec]
            [failjure.core :as f]
            [cljs.core.async :refer [chan <! >!]]
            [devneya.prompt :as prompt]
            [devneya.utils :refer [chan->promise log-with-id date-hms]]
            [devneya.gpt :as gpt])
  (:require-macros [failjure.core]
                   [cljs.core.async.macros :refer [go]]))

(defn make-initial-prompt
  ([log-id make-api-request security-key prompt]
   (log-with-id log-id "Making initial prompt")
   (gen/generate-code-async log-id
                            make-api-request
                            security-key
                            (str "Write only code. Do not use ```. " prompt)))
  ([make-api-request security-key prompt]
   (make-initial-prompt (date-hms) make-api-request security-key prompt)))

(defn make-fix-prompt
  "Get api key, generated code, its execution error and attempt number.\n
   Make generate code request to fix.\n
   Return async channel with fixed code or with fail."
  [log-id make-api-request security-key generated-code check-error attempt]
  (log-with-id log-id (str "Making fix prompt number " attempt))
  (gen/generate-code-async log-id
                           make-api-request
                           security-key
                           (str "Here is a code:\n"
                                generated-code
                                "\nThere is problem with this code:\n"
                                check-error
                                "\nRewrite code to fix it. Write only code. Do not use ```.")))

(defn make-fix-prompt-chain
  "Get api key, request attempt limit, current code generation channel and attempt number.\n
   Make fix prompt chain: try to execute current code and send fix prompt, if needed, *MAX_REPS* times.\n
   Return async channel with fail if couldn't fix the code and if it occured on some generation request, or fixed code otherwise."
  ([log-id make-api-request security-key attempt-limit check-code generated-code-channel attempt]
   (log-with-id log-id "Fix prompt chain started")
   (let [out-chan (chan)]
     (go (>! out-chan (f/if-let-failed?
                       [generated-code-or-fail (<! generated-code-channel)]
                       generated-code-or-fail
                       (f/if-let-failed?
                        [check-result (<! (check-code log-id generated-code-or-fail))]
                        (do (log-with-id log-id (str "Code check failed on attempt " attempt "! Retrying..."))
                            (if (< attempt attempt-limit)
                              (<! (make-fix-prompt-chain
                                   log-id
                                   make-api-request
                                   security-key
                                   attempt-limit
                                   check-code
                                   (make-fix-prompt log-id make-api-request security-key generated-code-or-fail check-result attempt)
                                   (inc attempt)))
                              (f/fail "Couldn't generate working code for the given request.\n")))
                        generated-code-or-fail))))
     out-chan))
  ([log-id make-api-request security-key attempt-limit generated-code-channel]
   (make-fix-prompt-chain log-id make-api-request security-key attempt-limit exec/exec-code generated-code-channel 1)))

(defn make-prompt-chain
  "Get api key, request attempt limit and prompt.\n
   Make prompt chain: one initial prompt, then start fix prompt chain.\n
   Return async channel with fail, if couldn't generate working code and if it occured on some prompt or with working code."
  ([log-id make-api-request security-key attempt-limit prompt]
   (make-fix-prompt-chain
    log-id
    make-api-request
    security-key
    attempt-limit
    (make-initial-prompt log-id make-api-request security-key prompt)))
  ([log-id security-key attempt-limit prompt]
   (make-prompt-chain log-id gpt/get-chatgpt-api-async-response security-key attempt-limit prompt)))

(defn make-prompt-promise
  ([log-id openai-key attempt-limit prompt]
   (chan->promise (make-prompt-chain log-id openai-key attempt-limit prompt)))
  ([openai-key attempt-limit prompt]
   (chan->promise (make-prompt-chain (date-hms) openai-key attempt-limit prompt))))

(defn make-request
  [openai-key prompt]
  (chan->promise (make-initial-prompt gpt/get-chatgpt-api-async-response openai-key prompt)))

(:export make-prompt-promise)
(:export make-request)