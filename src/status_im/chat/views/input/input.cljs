(ns status-im.chat.views.input.input
  (:require-macros [status-im.utils.views :refer [defview]])
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [taoensso.timbre :as log]
            [status-im.accessibility-ids :as id]
            [status-im.components.react :refer [view
                                                text
                                                text-input
                                                icon
                                                touchable-highlight
                                                dismiss-keyboard!]]
            [status-im.chat.views.input.emoji :as emoji]
            [status-im.chat.views.input.parameter-box :as parameter-box]
            [status-im.chat.views.input.result-box :as result-box]
            [status-im.chat.views.input.suggestions :as suggestions]
            [status-im.chat.views.input.validation-messages :as validation-messages]
            [status-im.chat.styles.input.input :as style]
            [status-im.chat.utils :as utils]))

(defview commands-view []
  []
  [view style/commands-root
   [touchable-highlight {:on-press #(do (dispatch [:toggle-chat-ui-props :show-suggestions?])
                                        (dispatch [:update-suggestions]))}
    [view style/commands-list-icon
     [icon :input_list style/commands-list-icon]]]
   [view style/commands
    [text {:style style/command
           :font  :roboto-mono}
     "/browse"]
    [text {:style style/command
           :font  :roboto-mono}
     "/send"]]])

(defn- invisible-input [{:keys [set-layout-width value]}]
  [text {:style     style/invisible-input-text
         :on-layout #(let [w (-> (.-nativeEvent %)
                                 (.-layout)
                                 (.-width))]
                       (set-layout-width w))}
   (utils/safe-trim value)])

(defn- input-helper [{:keys [command width]}]
  (when (and command
             (empty? (:args command)))
    (when-let [placeholder (get-in command [:command :params 0 :placeholder])]
      [text {:style (style/input-helper-text width)}
       placeholder])))

(defn input-view []
  (let [component            (r/current-component)
        set-layout-width     #(r/set-state component {:width %})
        set-layout-height    #(r/set-state component {:height %})
        input-text           (subscribe [:chat :input-text])
        masked-text          (subscribe [:chat :masked-text])
        current-param        (subscribe [:chat :current-param])
        command              (subscribe [:selected-chat-command])
        sending-in-progress? (subscribe [:chat-ui-props :sending-in-progress?])]
    (r/create-class
      {:component-will-mount
       (fn []
         (dispatch [:update-suggestions]))

       :reagent-render
       (fn []
         (let [{:keys [width height]} (r/state component)
               command @command]
           [view (style/input-root height command)
            [text-input
             {:ref                    #(dispatch [:set-chat-ui-props :input-ref %])
              :accessibility-label    id/chat-message-input
              :blur-on-submit         true
              :multiline              true
              :default-value          (or @masked-text @input-text "")
              :editable               (not @sending-in-progress?)
              :on-blur                #(do (dispatch [:send-current-message])
                                           (dispatch [:set-chat-ui-props :input-focused? false])
                                           (set-layout-height 0))
              :on-change-text         #(when-not (str/includes? % "\n")
                                         (do (dispatch [:set-chat-input-text %])
                                             (dispatch [:load-chat-parameter-box (:command command)])
                                             (when (not command)
                                               (dispatch [:set-chat-input-metadata nil])
                                               (dispatch [:set-chat-ui-props :result-box nil]))
                                             (dispatch [:set-chat-ui-props :validation-messages nil])))
              :on-content-size-change #(let [h (-> (.-nativeEvent %)
                                                   (.-contentSize)
                                                   (.-height))]
                                         (set-layout-height h))
              :on-selection-change    #(let [s (-> (.-nativeEvent %)
                                                   (.-selection))]
                                         (dispatch [:set-chat-ui-props :selection {:start (.-start s)
                                                                                   :end   (.-end s)}]))
              :on-submit-editing      #(do (dispatch [:set-chat-ui-props :sending-in-progress? true])
                                           (dispatch [:send-current-message]))
              :on-focus               #(do (dispatch [:set-chat-ui-props :input-focused? true])
                                           (dispatch [:set-chat-ui-props :show-emoji? false]))
              :style                  style/input-view}]
            [invisible-input {:value            (or @masked-text @input-text)
                              :set-layout-width set-layout-width}]
            [input-helper {:command command
                           :width   width}]
            (if-not command
              [touchable-highlight
               {:on-press #(do (dispatch [:toggle-chat-ui-props :show-emoji?])
                               (dismiss-keyboard!))}
               [view
                [icon :smile style/input-emoji-icon]]]
              [touchable-highlight
               {:on-press #(do (dispatch [:set-chat-input-text nil])
                               (dispatch [:set-chat-input-metadata nil])
                               (dispatch [:set-chat-ui-props :result-box nil])
                               (dispatch [:set-chat-ui-props :validation-messages nil]))}
               [view style/input-clear-container
                [icon :close_gray style/input-clear-icon]]])]))})))

(defview input-container []
  [command-complete? [:command-complete?]]
  [view style/input-container
   [input-view]
   (when command-complete?
     [touchable-highlight {:on-press #(dispatch [:send-current-message])}
      [view style/send-message-container
       [icon :arrow_top style/send-message-icon]]])])

(defview container []
  [margin [:chat-input-margin]
   show-emoji? [:chat-ui-props :show-emoji?]
   selected-chat-command [:selected-chat-command]]
  [view
   [parameter-box/parameter-box-view]
   [result-box/result-box-view]
   [suggestions/suggestions-view]
   [validation-messages/validation-messages-view]
   [view {:style     (style/root margin)
          :on-layout #(let [h (-> (.-nativeEvent %)
                                  (.-layout)
                                  (.-height))]
                        (dispatch [:set-chat-ui-props :input-height h]))}
    [view (style/container selected-chat-command)
     (when-not selected-chat-command
       [commands-view])
     [input-container]]
    (when show-emoji?
      [emoji/emoji-view])]])
