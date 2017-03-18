(ns status-im.chat.views.input.validation-messages
  (:require-macros [status-im.utils.views :refer [defview]])
  (:require [status-im.components.react :as c]
            [status-im.chat.styles.input.validation-message :as style]
            [status-im.utils.listview :as lw]))

(defn messages-list [markup]
  [c/view {:flex 1}
   markup])

(defview validation-messages-view []
  [chat-input-margin [:chat-input-margin]
   input-height [:chat-ui-props :input-height]
   {:keys [markup] :as validation-messages} [:chat-ui-props :validation-messages]]
  (when validation-messages
    [c/view (style/root (+ input-height chat-input-margin))
     [messages-list markup]]))
