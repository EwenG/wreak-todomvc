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


(extend-type cljs.core.async.impl.channels/ManyToManyChannel
  ds/IPublish
  (publish [this report]
    (async/put! this report)))

(defn only
  "Return the only item from a query result"
  ([query-result]
   (assert (= 1 (count query-result)))
   (assert (= 1 (count (first query-result))))
   (ffirst query-result))
  ([query-result default]
   (if (= 0 (count query-result)) default
                                  (only query-result))))

(defn set-attr! [app id attr val]
  (ds/transact! app [{:db/id id
                      attr val}]))

(defn id-for-attr [db in-attr]
  (-> (ds/q '[:find ?id
              :in $ attr
              :where [?id attr _]]
            db in-attr)
      only))


(def header
  (component "header"
             {:render (fn [_ _ _]
                        (html [:div
                               [:h1 "todos"]
                               [:input#new-todo {:placeholder "What needs to be done?" :autofocus true}]]))}))




(def get-display-main (-> (query
                            [data] '[:find ?display-main
                                     :where [_ :todo-mvc/display-main ?display-main]]
                            data)
                          (ds/wrap-query only)))


(defn set-display-main! [app val]
  (let [id (id-for-attr @app :todo-mvc/display-main)]
    (set-attr! app id :todo-mvc/display-main val)))

(defn listen-display-main [{:keys [tx-data]}]
  (when-let [tx-data (->> (filter :added tx-data)
                          last)]
    (match tx-data
           {:e     _
            :a     :todo-mvc/display-main
            :v     val
            :added true} val)))

(def main
  (component "main"
             {:render (fn [_ {:keys [display-main]} _]
                        (html [:div {:style {:display (if display-main "block" "none")}}
                               [:input#toggle-all {:type "checkbox"}]
                               [:label {:for "toggle-all"} "Mark all as complete"]
                               [:ul#todo-list]]))
              :getInitialState      (fn [_ {:keys [app]}]
                                      {:display-main (get-display-main @app)})
              :componentDidMount    (fn [_ _ {:keys [app]}]
                                      (let [display-main-transducer (map listen-display-main)
                                            display-main-chan (async/chan 1 display-main-transducer)
                                            index-keys (ds/get-index-keys get-display-main app)
                                            comp *component*]
                                        (aset comp ::display-main-chan display-main-chan)
                                        (ds/listen! app display-main-chan index-keys)
                                        (go-loop []
                                                 (let [val (async/<! display-main-chan)]
                                                   (when (not (nil? val))
                                                     (w/replace-state! comp (merge (w/get-state comp)
                                                                                   {:display-main val}))
                                                     (recur))
                                                   (async/close! display-main-chan)))))
              :componentWillUnmount (fn [_ _ {:keys [app]}]
                                      (ds/unlisten! app (aget *component* ::display-main-chan)))}))


(def footer
  (component "footer"
             {:render (fn [_ _ _]
                        (html [:div
                               [:span#todo-count]
                               [:ul#filters
                                [:li [:a.selected {:href "#/"} "All"]]
                                [:li [:a.selected {:href "#/active"} "Active"]]
                                [:li [:a.selected {:href "#/completed"} "Completed"]]]
                               [:button#clear-completed "Clear completed"]]))}))





(defn load-app []
  (let [conn (ds/create-conn)]
    (ds/transact! conn [{:db/id -1
                         :todo-mvc/display-main false}])
    conn))

(def app (load-app))

(w/render (header nil {:app app})
          (-> (sel "#header") single-node))

(w/render (main nil {:app app})
          (-> (sel "#main") single-node))

(w/render (footer nil {:app app})
          (-> (sel "#footer") single-node))


