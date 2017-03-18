(ns status-im.chat.views.input.result-box
  (:require-macros [status-im.utils.views :refer [defview]])
  (:require [re-frame.core :refer [subscribe dispatch]]
            [status-im.components.react :refer [view
                                                scroll-view
                                                touchable-highlight
                                                text
                                                icon]]
            [status-im.chat.styles.input.result-box :as style]
            [status-im.i18n :refer [label]]
            [taoensso.timbre :as log]))

(defn header [title]
  [view {:style style/header-container}
   [view style/header-icon]
   [view style/header-title-container
    [text {:style style/header-title-text
           :font :medium}
     title]
    [touchable-highlight {:on-press #(dispatch [:set-chat-ui-props :result-box nil])}
     [view style/header-close-container
      [icon :close_gray style/header-close-icon]]]]])

(defview result-box-container [markup]
  [view {:flex 1}
   markup])

(defview result-box-view []
  [chat-input-margin [:chat-input-margin]
   input-height [:chat-ui-props :input-height]
   {:keys [markup title] :as result-box} [:chat-ui-props :result-box]]
  (when result-box
    [view (style/root 250 (+ input-height chat-input-margin))
     [header title]
     [result-box-container markup]]))