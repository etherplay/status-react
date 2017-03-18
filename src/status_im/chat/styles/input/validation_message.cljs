(ns status-im.chat.styles.input.validation-message
  (:require [status-im.chat.constants :as constants]
            [status-im.components.styles :as common]))

(defn root [bottom]
  {:flex-direction   :column
   :left             0
   :right            0
   :bottom           bottom
   :position         :absolute})
