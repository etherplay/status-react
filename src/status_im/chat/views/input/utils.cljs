(ns status-im.chat.views.input.utils
  (:require [taoensso.timbre :as log]
            [status-im.utils.platform :refer [platform-specific]]))

;;(get-in platform-specific [:component-styles :status-bar :default :height])

(defn max-area-height [default-height bottom screen-height]
  (log/debug "ALWX screen-height" screen-height)
  (if (> (+ bottom default-height) screen-height)
    (- screen-height bottom)
    default-height))