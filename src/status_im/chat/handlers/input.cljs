(ns status-im.chat.handlers.input
  (:require [re-frame.core :refer [enrich after dispatch]]
            [taoensso.timbre :as log]
            [status-im.chat.constants :as const]
            [status-im.chat.models.input :as input-model]
            [status-im.chat.models.password-input :as password-input]
            [status-im.chat.suggestions :as suggestions]
            [status-im.components.react :as react-comp]
            [status-im.components.status :as status]
            [status-im.utils.datetime :as time]
            [status-im.utils.handlers :as handlers]
            [status-im.utils.random :as random]
            [clojure.string :as str]))

(defn- changed-arg-position [xs ys]
  (let [longest  (into [] (max-key count xs ys))
        shortest (into [] (if (= longest xs) ys xs))]
    (->> longest
         (map-indexed (fn [index x]
                        (if (and (> (count shortest) index)
                                 (= x (get shortest index)))
                          nil
                          index)))
         (remove nil?)
         (first))))

(def text-maskers
  [])

(handlers/register-handler
  :set-chat-input-text
  (fn [{:keys [current-chat-id chats chat-ui-props] :as db} [_ text chat-id]]
    (let [chat-id   (or chat-id current-chat-id)
          selection (get-in chat-ui-props [chat-id :selection])]
      (dispatch [:update-suggestions chat-id text])
      ;; the code bellow allows text maskers to work
      (if-let [{old-command :command
                old-args    :args} (input-model/selected-chat-command db chat-id)]
        (let [new-text      (into [] (str/split text const/spacing-char))
              new-args      (rest new-text)
              arg-pos       (changed-arg-position old-args new-args)
              current-param (get-in old-command [:params arg-pos])
              text-masker   (first (filter #((:execute-when %) current-param) text-maskers))
              new-params    {:masked-text   (when text-masker
                                              (let [{:keys [get-masked-text]} text-masker]
                                                (get-masked-text new-text arg-pos)))
                             :input-text    (if text-masker
                                              (let [{:keys [make-change]} text-masker]
                                                (make-change {:old-command old-command
                                                              :old-args    old-args
                                                              :new-args    new-args
                                                              :arg-pos     arg-pos
                                                              :selection   selection}))
                                              text)
                             :current-param current-param}]
          (update-in db [:chats chat-id] merge new-params))
        (cond-> (assoc-in db [:chats chat-id :input-text] text)
                (nil? text) (update-in [:chats chat-id] merge {:masked-text   nil
                                                               :current-param nil}))))))

(handlers/register-handler
  :add-to-chat-input-text
  (handlers/side-effect!
    (fn [{:keys [chats current-chat-id]} [_ text-to-add]]
      (let [input-text (get-in chats [current-chat-id :input-text])]
        (dispatch [:set-chat-input-text (str input-text text-to-add)])))))

(handlers/register-handler
  :select-chat-input-command
  (handlers/side-effect!
    (fn [{:keys [current-chat-id chat-ui-props] :as db} [_ {:keys [name prefill] :as command} metadata]]
      (dispatch [:set-chat-input-text (str const/command-char
                                           name
                                           const/spacing-char
                                           (str/join const/spacing-char prefill))])
      (dispatch [:set-chat-input-metadata metadata])
      (dispatch [:set-chat-ui-props :show-suggestions? false])
      (dispatch [:set-chat-ui-props :result-box nil])
      (dispatch [:set-chat-ui-props :validation-messages nil])
      (dispatch [:load-chat-parameter-box command 0])
      (dispatch [:chat-input-focus]))))

(handlers/register-handler
  :set-chat-input-metadata
  (fn [{:keys [current-chat-id] :as db} [_ data chat-id]]
    (let [chat-id (or chat-id current-chat-id)]
      (assoc-in db [:chats chat-id :input-metadata] data))))

(handlers/register-handler
  :set-command-argument
  (handlers/side-effect!
    (fn [{:keys [current-chat-id] :as db} [_ [index arg]]]
      (let [command      (-> (get-in db [:chats current-chat-id :input-text])
                             (str/split const/spacing-char))
            command-name (first command)
            command-args (into [] (rest command))
            command-args (if (< index (count command-args))
                           (assoc command-args index arg)
                           (conj command-args arg))]
        (dispatch [:set-chat-input-text (str command-name
                                             const/spacing-char
                                             (str/join const/spacing-char command-args)
                                             const/spacing-char)])))))

(handlers/register-handler
  :chat-input-focus
  (handlers/side-effect!
    (fn [{:keys [current-chat-id chat-ui-props] :as db}]
      (when-let [ref (get-in chat-ui-props [current-chat-id :input-ref])]
        (.focus ref)))))

(handlers/register-handler
  :update-suggestions
  (fn [{:keys [current-chat-id] :as db} [_ chat-id text]]
    (let [chat-id     (or chat-id current-chat-id)
          chat-text   (or text (get-in db [:chats chat-id :input-text]) "")
          requests    (suggestions/get-request-suggestions db chat-text)
          suggestions (suggestions/get-command-suggestions db chat-text)
          {:keys [dapp?]} (get-in db [:contacts chat-id])]
      (-> db
          (assoc-in [:chats chat-id :request-suggestions] requests)
          (assoc-in [:chats chat-id :command-suggestions] suggestions)))))

(handlers/register-handler
  :load-chat-parameter-box
  (handlers/side-effect!
    (fn [{:keys [current-chat-id] :as db} [_ {:keys [name type] :as command}]]
      (let [parameter-index (input-model/argument-position db current-chat-id)]
        (when (and command (> parameter-index -1))
          (let [data   (get-in db [:local-storage current-chat-id])
                path   [(if (= :command type) :commands :responses)
                        name
                        :params
                        parameter-index
                        :suggestions]
                args   (-> (get-in db [:chats current-chat-id :input-text])
                           (str/split const/spacing-char)
                           (rest))
                params {:parameters {:args args}
                        :context    {:data data}}]
            (status/call-jail current-chat-id
                              path
                              params
                              #(dispatch [:suggestions-handler
                                          {:chat-id         current-chat-id
                                           :command         command
                                           :parameter-index parameter-index
                                           :result          %}]))))))))

(handlers/register-handler
  ::send-message
  (handlers/side-effect!
    (fn [{:keys [current-public-key current-account-id] :as db} [_ command-message chat-id]]
      (let [text    (get-in db [:chats chat-id :input-text])
            data    {:message  text
                     :command  command-message
                     :chat-id  chat-id
                     :identity current-public-key
                     :address  current-account-id}]
        (dispatch [:set-chat-input-text nil chat-id])
        (dispatch [:set-chat-input-metadata nil chat-id])
        (dispatch [:set-chat-ui-props :sending-disabled? true])
        (cond
          command-message
          (dispatch [:check-commands-handlers! data])
          (not (str/blank? text))
          (dispatch [:prepare-message data]))))))

(handlers/register-handler
  ::proceed-command
  (handlers/side-effect!
    (fn [db [_ command chat-id]]
      (dispatch [::request-command-data
                 {:command   command
                  :chat-id   chat-id
                  :data-type :validator
                  :after     #(dispatch [::proceed-validation-messages command chat-id %2])}]))))

(handlers/register-handler
  ::proceed-validation-messages
  (handlers/side-effect!
    (fn [db [_ command chat-id errors]]
      (if errors
        (dispatch [:set-chat-ui-props :validation-messages errors])
        (dispatch [::request-command-data
                   {:command   command
                    :chat-id   chat-id
                    :data-type :on-send
                    :after     #(dispatch [::send-command %2 command chat-id])}])))))


(handlers/register-handler
  ::send-command
  (handlers/side-effect!
    (fn [db [_ on-send command chat-id]]
      (if on-send
        (do
          (dispatch [:set-chat-ui-props :result-box on-send])
          (react-comp/dismiss-keyboard!))
        (dispatch [::request-command-data
                   {:command   command
                    :chat-id   chat-id
                    :data-type :preview
                    :after     #(dispatch [::send-message % chat-id])}])))))

(handlers/register-handler
  ::request-command-data
  (handlers/side-effect!
    (fn [db [_ {{:keys [command metadata args] :as c} :command
                :keys [chat-id data-type after]}]]
      (let [message-id      (random/id)
            params          (input-model/args->params c)
            command-message {:command    command
                             :params     params
                             :to-message (:to-message-id metadata)
                             :created-at (time/now-ms)
                             :id         message-id
                             :chat-id    chat-id}
            request-data    {:message-id   message-id
                             :chat-id      chat-id
                             :content      {:command (:name command)
                                            :params  {:metadata metadata
                                                      :args     params}
                                            :type    (:type command)}
                             :on-requested #(after command-message %)}]
        (dispatch [:request-command-data request-data data-type])))))

(handlers/register-handler
  :send-current-message
  (handlers/side-effect!
    (fn [{:keys [current-chat-id] :as db} [_ chat-id]]
      (let [chat-id      (or chat-id current-chat-id)
            chat-command (input-model/selected-chat-command db chat-id)]
        (if chat-command
          (when (input-model/command-complete? chat-command)
            (dispatch [::proceed-command chat-command chat-id]))
          (dispatch [::send-message nil chat-id]))))))