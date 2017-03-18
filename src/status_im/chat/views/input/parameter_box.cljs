(ns status-im.chat.views.input.parameter-box
  (:require-macros [status-im.utils.views :refer [defview]])
  (:require [re-frame.core :refer [subscribe dispatch]]
            [status-im.components.react :refer [view
                                                scroll-view
                                                touchable-highlight
                                                text
                                                icon]]
            [status-im.chat.styles.input.parameter-box :as style]
            [status-im.i18n :refer [label]]
            [taoensso.timbre :as log]))

(defn header []
  [view {:style style/header-container}
   [view style/header-icon]])

(defview parameter-box-container []
  [parameter-box [:chat-parameter-box]]
  (when (:hiccup parameter-box)
    (:hiccup parameter-box)))

(defview parameter-box-view []
  [chat-input-margin [:chat-input-margin]
   input-height [:chat-ui-props :input-height]
   chat-parameter-box [:chat-parameter-box]]
  (when chat-parameter-box
    [view (style/root 250 (+ input-height chat-input-margin))
     [header]
     [parameter-box-container]
     [view {:flex 1}]]))