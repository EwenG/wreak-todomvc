(ns ewen.wreak.todo-mvc
  (:require [ewen.wreak :refer [component *component*] :as w]
            [sablono.core :refer-macros [html]]
            [datascript :as ds]
            [domina :refer [single-node]]
            [domina.css :refer [sel]]
            [domina.events :refer [listen! unlisten!
                                   prevent-default raw-event
                                   listen-once! current-target]]
            [cljs.core.async :as async]
            [cljs.core.match])
  (:require-macros [datascript :refer [defquery query]]
                   [cljs.core.async.macros :refer [go-loop]]
                   [cljs.core.match.macros :refer [match]]))


(def b (component "b"
                  {:render (fn [_ _ _]
                             (html [:div#b]))}))


(def a (component "a"
                  {:render (fn [_ _ _]
                             (html [:div#a (b nil nil)]))}))



(def root (w/render (a nil nil)
                    (-> (sel "#root") single-node)))

(.log js/console "root " root)

(.log js/console (coll? (transient [])))


