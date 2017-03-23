(ns status-im.chat.models.password-input
  (:require [status-im.chat.constants :as const]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn- get-masked-text [text arg-pos]
  (let [hide-fn      #(apply str (repeat (count %) const/masking-char))
        updated-text (update text (inc arg-pos) hide-fn)]
    (str/join const/spacing-char updated-text)))

(defn- get-change [{:keys [old-command old-args new-args arg-pos selection]}]
  (let [old-args      (into [] old-args)
        new-args      (into [] new-args)
        modification  (- (count (get new-args arg-pos))
                         (count (get old-args arg-pos)))
        type          (if (> modification 0) :added :removed)
        position      (-> (:start selection)
                          (- (+ (count (:name old-command)) 2))
                          (- (count (str/join const/spacing-char (take arg-pos old-args)))))
        position      (if (= :added type) (dec position) position)
        symbols-count (.abs js/Math modification)]
    {:type     type
     :position position
     :symbols  (when (= :added type)
                 (subs (get new-args arg-pos)
                       position
                       (+ position symbols-count)))}))

(defn- make-change [{:keys [old-command old-args new-args arg-pos selection] :as args}]
  (let [{:keys [type position symbols]} (get-change args)
        make-change  #(if (= type :added)
                        (str (subs % 0 position) symbols (subs % position))
                        (str (subs % 0 position) (subs % (+ 1 position (count symbols)))))
        args         (if (= (count old-args) 0)
                       [const/spacing-char]
                       (into [] old-args))
        updated-args (update args arg-pos make-change)]
    (str const/command-char
         (:name old-command)
         const/spacing-char
         (str/join const/spacing-char updated-args))))

(def masker
  {:execute-when    :hidden
   :make-change     make-change
   :get-masked-text get-masked-text})