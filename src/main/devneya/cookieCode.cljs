(ns devneya.cookieCode
  (:require [cljs.core.async :refer [<!]]
            [devneya.prompt :as prompt]
            [devneya.utils :refer [date-hms log-with-id toggle-class]]
            [failjure.core :as f]
            [reagent.core :as r]
            [reagent.dom :as rdom])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn cookie-code-form-template []
  (let [prompt (r/atom "")
        response (r/atom "")
        openai-key (r/atom "")]
    (fn []
      [:form {:class "cookie-code-form" :on-submit (fn [e]
                                                     (.preventDefault e)
                                                     (go
                                                       (let [current-time (date-hms)
                                                             resp (f/attempt f/message (<! (prompt/make-prompt-chain
                                                                                            current-time
                                                                                            @openai-key
                                                                                            3
                                                                                            @prompt)))]
                                                         (log-with-id current-time (str "returned on click to react: " resp))
                                                         (reset! response resp))))}
       [:div {:class ["cookie-code-form__close-button"]  :unselectable "on"} "+"]
       [:label {:class "cookie-code-form__label" :for "cookie-code-form-api-key"} "API Key:"]
       [:input {:type "text" :class "cookie-code-form__api-key" :name "cookie-code-form__api-key" :placeholder "API Key" :required true :value @openai-key :on-change #(reset! openai-key (-> % .-target .-value))}]
       [:label {:class "cookie-code-form__label" :for "cookie-code-form__prompt"} "Prompt:"]
       [:textarea {:class "cookie-code-form__prompt" :name "cookie-code-form__prompt" :placeholder "Prompt" :required true :value @prompt :on-change #(reset! prompt (-> % .-target .-value))}]
       [:button {:class "cookie-code-form__submit-button cookie-code-form__button" :type "submit"} "Submit"]
       [:textarea {:class "cookie-code-form__response" :name "cookie-code-form__response" :type "response" :value @response}]
       [:button {:class "cookie-code-form__copy-button cookie-code-form__button" :type "button"} "Copy & Close"]])))

(def cookie-code-container (-> js/document
                              (.getElementsByClassName "cookie-code-container")
                              (.item 0)))
(def cookie-code-popup-button (-> js/document
                                 (.getElementsByClassName "cookie-code-popup-button")
                                 (.item 0)))



(defn add-click-listener []
  (let [cookie-code-form (-> js/document
                             (.getElementsByClassName "cookie-code-form")
                             (.item 0))]
    (when cookie-code-form (.addEventListener cookie-code-popup-button "click" #(toggle-class cookie-code-form "active")))))


(defn render-cookie-code []
  (rdom/render [cookie-code-form-template] cookie-code-container)
  (add-click-listener))


