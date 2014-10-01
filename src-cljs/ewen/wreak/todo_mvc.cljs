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
                   [cljs.core.async.macros :refer [go go-loop]]
                   [cljs.core.match.macros :refer [match]]))

(defn load-app []
  (let [conn (ds/create-conn)]
    (ds/transact! conn [{:db/id -1
                         :password/label "Password1"
                         :state/dragging false
                         :state/sort-index 0}
                        {:db/id -2
                         :password/label "Password2"
                         :state/dragging false
                         :state/sort-index 1}
                        {:db/id -3
                         :view/current :home}])
    conn))

(def conn (load-app))


(def c (component "c"
                  {:render (fn [_ _ _]
                             (html [:div#c]))}))

(def b (component "b"
                  {:render (fn [_ _ _]
                             (html [:div#b]))}))


(defquery get-list-passwords
          [data] '[:find ?id
                   :in $
                   :where [?id :password/label ?label]] data)

(def a (component "a"
                  {:render (fn [props state _]
                             (.log js/console (str "render-state " state))
                             (if (:e state)
                               (html [:div#a (b nil)])
                               (html [:div#a (c nil)])))
                   :getInitialState (fn [props db]
                                 {:e "e"})
                   :dbDidUpdate (fn [_ state {:keys [tx-data tx-index-keys] :as tx-data}]
                                  #_(.log js/console (str tx-data))
                                  {:e2 "e2"})
                   :stateDidUpdate (fn [_ old-state new-state]
                                  #_(.log js/console (str "old-state " old-state "new-state " new-state)))
                   :componentDidMount (fn []
                                        )}))


(def root (w/render a {:key 1 :prop {:props "prop2"} :prop01 "prop01"}
                    (-> (sel "#root") single-node)
                    @conn conn))


(js/setTimeout #(ds/transact! conn [{:db/id          1
                                :password/label "e"}])
               3000)


#_(let [a #js {:depth 0}
      b #js {:depth 1 :ancestor a}
      c #js {:depth 1 :ancestor a}]
  (.log js/console (w/lowest-common-ancestor #{b c})))



