(ns ewen.wreak.todo-mvc
  (:require [ewen.wreak :refer [component *component* mixin] :as w]
            [sablono.core :refer-macros [html]]
            [datascript :as ds]
            [domina :refer [single-node]]
            [domina.css :refer [sel]]
            [domina.events :refer [listen! unlisten!
                                   prevent-default raw-event
                                   listen-once! current-target]]
            [cljs.core.async :as async]
            [cljs.core.match])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [cljs.core.match :refer [match]]))

(defn set-attr! [conn id attr val]
  (ds/transact! conn [{:db/id id
                       attr   val}]))

(defn only
  "Return the only item from a query result"
  ([query-result]
   (assert (= 1 (count query-result)))
   (assert (= 1 (count (first query-result))))
   (ffirst query-result))
  ([query-result default]
   (if (= 0 (count query-result)) default
       (only query-result))))





(defn add-todo! [conn text]
  (ds/transact! conn [{:db/id -1
                       :todo-item/title text
                       :todo-item/done false}]))


(defn get-todos [db]
  (->> (ds/q '[:find ?id ?title ?done
               :where [?id :todo-item/title ?title]
               [?id :todo-item/done ?done]]
             db)
       (reduce (fn [items [id title done]]
                 (assoc items id {:id id :title title :done done}))
               {})))

(defn retract-all-done [db]
  (let [done-items (->> (get-todos db)
                        (filter (comp :done val)))]
    (mapv (fn [[k _]] [:db.fn/retractEntity k]) done-items)))

(defn complete-all [db v]
  (let [items (get-todos db)]
    (mapv (fn [[k _]]
            {:db/id k
             :todo-item/done v})
          items)))


(defn complete-all! [conn v]
  (ds/transact! conn [[:db.fn/call complete-all v]]))

(defn clear-done! [conn]
  (ds/transact! conn [[:db.fn/call retract-all-done]]))

(defn get-filt [db]
  (-> (ds/q '[:find ?filt
              :where [_ :todo-mvc/filt ?filt]]
            db)
      only))

(defn get-filt-id [db]
  (-> (ds/q '[:find ?id
              :where [?id :todo-mvc/filt _]]
            db)
      only))

(defn set-filt! [conn val]
  (ds/transact! conn [{:db/id (get-filt-id @conn)
                       :todo-mvc/filt val}]))

(def todo-input
  (component
   "todo-input"
   (mixin
    {:render
     (fn [{{:keys [title]} :state
           conn :conn}]
       (let [id (aget *component* :ewen.wreak/id)]
         (html [:input
                {:id "new-todo"
                 :type      "text"
                 :value     title
                 :placeholder "What needs to be done?"
                 :on-change
                 #(set-attr!
                   conn
                   id
                   :input-mixin/val
                   (-> % .-target .-value))
                 :onKeyUp
                 #(case (.-which %)
                    13 (do (add-todo!
                            conn
                            (-> title
                                str
                                clojure.string/trim))
                           (set-attr!
                            conn id :input-mixin/val ""))
                    27 (set-attr!
                        conn id :input-mixin/val "")
                    nil)}])))
     :getInitialState (fn [{:keys [db]}]
                        (let [id (aget *component* :ewen.wreak/id)]
                          {:title (->
                                   (ds/entity db id)
                                   :input-mixin/val)}))
     :dbDidUpdate     (fn [{state :state
                            {:keys [db-after]} :tx-report}]
                        (let [id (aget *component* :ewen.wreak/id)]
                          (assoc state
                                 :title
                                 (-> (ds/entity db-after id)
                                     :input-mixin/val))))}
    (w/component-id-mixin "input-mixin"))))




(def todo-edit
  (component
   "todo-edit"
   (mixin
    {:render
     (fn
       [{{:keys [id]} :props
         {:keys [title]} :state
         conn :conn}]
       (let [mixin-id (aget *component* :ewen.wreak/id)]
         (html [:input
                {:type      "text"
                 :value       title
                 :className "edit"
                 :on-change
                 #(set-attr!
                   conn
                   mixin-id
                   :input-mixin/val
                   (-> % .-target .-value))
                 :onKeyUp
                 #(case (.-which %)
                    13 (do (set-attr!
                            conn id :todo-item/title
                            (-> title
                                str
                                clojure.string/trim))
                           (set-attr!
                            conn
                            id
                            :todo-item/editing false))
                    27 (set-attr!
                        conn
                        id
                        :todo-item/editing false)
                    nil)}])))
     :componentWillMount (fn [{{:keys [id]} :props conn :conn}]
                           (let [mixin-id (aget
                                           *component*
                                           :ewen.wreak/id)]
                             (set-attr! conn mixin-id
                                        :input-mixin/val
                                        (-> (ds/entity @conn id)
                                            :todo-item/title))))
     :componentDidMount (fn []
                          (.focus (.getDOMNode *component*)))
     :getInitialState (fn [{:keys [db]}]
                        (let [id (aget *component* :ewen.wreak/id)]
                          {:title (->
                                   (ds/entity db id)
                                   :input-mixin/val)}))
     :dbDidUpdate     (fn [{state :state
                            {:keys [db-after]} :tx-report}]
                        (let [id (aget *component* :ewen.wreak/id)]
                          (assoc state
                                 :title
                                 (-> (ds/entity db-after id)
                                     :input-mixin/val))))}
    (w/component-id-mixin "input-mixin"))))


(defn toggle [db id attr]
  (let [val (-> (ds/entity db id) attr)]
    [{:db/id id
      attr (not val)}]))

(defn toggle-done [conn id]
  (ds/transact! conn [[:db.fn/call toggle id :todo-item/done]]))

(defn delete [conn id]
  (ds/transact! conn [[:db.fn/retractEntity id]]))



(def todo-stats
  (component
   "todo-stats"
   {:render
    (fn
      [{{:keys [active done filt]} :state
        conn :conn}]
      (html [:div
             [:span#todo-count
              [:strong active] " " (case active 1 "item" "items") " left"]
             [:ul#filters
              [:li [:a {:class    (when (= :all filt) "selected")
                        :on-click #(set-filt! conn :all)} "All"]]
              [:li [:a {:class    (when (= :active filt) "selected")
                        :on-click #(set-filt! conn :active)} "Active"]]
              [:li [:a {:class    (when (= :done filt) "selected")
                        :on-click #(set-filt! conn :done)} "Completed"]]]
             (when (pos? done)
               [:button#clear-completed {:on-click #(clear-done! conn)}
                "Clear completed " done])]))
    :getInitialState (fn [{:keys [conn]}]
                       (let [items (vals (get-todos @conn))
                             done (->> items (filter :done) count)
                             active (- (count items) done)]
                         {:active active
                          :done done
                          :filt (get-filt @conn)}))
    :dbDidUpdate (fn [{{:keys [db-after]} :tx-report}]
                   (let [items (vals (get-todos db-after))
                         done (->> items (filter :done) count)
                         active (- (count items) done)]
                     {:active active
                      :done done
                      :filt (get-filt db-after)}))}))

(def todo-item
  (component
   "todo-item"
   {:render
    (fn
      [{{:keys [id done editing title]} :state
        conn :conn}]
      (html [:li {:class (str (if done "completed ")
                              (if editing "editing"))}
             [:div.view
              [:input.toggle {:type      "checkbox" :checked done
                              :on-change #(toggle-done conn id)}]
              [:label {:on-double-click
                       #(set-attr!
                         conn id :todo-item/editing true)}
               title]
              [:button.destroy {:on-click #(delete conn id)}]]
             (when editing
               (todo-edit {:id id}))]))
    :componentWillMount (fn [{{:keys [id]} :props conn :conn}]
                          (ds/transact! conn
                                        [{:db/id id
                                          :todo-item/editing false}]))
    :getInitialState (fn [{db :db {:keys [id]} :props}]
                       (let [ent (ds/entity db id)]
                         {:id (:db/id ent)
                          :title (:todo-item/title ent)
                          :done (:todo-item/done ent)
                          :editing (:todo-item/editing ent)}))
    :dbDidUpdate     (fn [{{:keys [id]}
                           :props {:keys [db-after]} :tx-report}]
                       (let [ent (ds/entity db-after id)]
                         {:id (:db/id ent)
                          :title (:todo-item/title ent)
                          :done (:todo-item/done ent)
                          :editing (:todo-item/editing ent)}))}))





(def todo-app
  (component
   "todo-app"
   {:render
    (fn
      [{{:keys [todos filt]} :state
        conn :conn}]
      (let [items (vals todos)
            done (->> items (filter :done) count)
            active (- (count items) done)]
        (html [:div
               [:section#todoapp
                [:header#header
                 [:h1 "todos"]
                 (todo-input nil)]
                (when (-> items count pos?)
                  [:div
                   [:section#main
                    [:input#toggle-all {:type "checkbox"
                                        :checked (zero? active)
                                        :on-change
                                        #(complete-all!
                                          conn (pos? active))}]
                    [:label {:for "toggle-all"} "Mark all as complete"]
                    [:ul#todo-list
                     (for [todo (filter (case filt
                                          :active (complement :done)
                                          :done :done
                                          :all identity) items)]
                       (todo-item {:key (:id todo)
                                   :id  (:id todo)}))]]
                   [:footer#footer
                    (todo-stats nil)]])]
               [:footer#info
                [:p "Double-click to edit a todo"]]])))
    :getInitialState (fn [{:keys [db]}]
                       {:todos (sort (get-todos db))
                        :filt (get-filt db)})
    :dbDidUpdate     (fn [{{:keys [db-after]} :tx-report}]
                       {:todos (sort (get-todos db-after))
                        :filt (get-filt db-after)})}))

(defn load-app []
  (let [conn (ds/create-conn)]
    (ds/transact! conn [{:db/id -1
                         :todo-mvc/filt :all}])
    conn))

(def conn (load-app))

(when-not (js/document.getElementById "root")
  (let [root (js/document.createElement "div")]
    (.setAttribute root "id" "root")
    (.appendChild (.-body js/document) root)))
(w/render todo-app nil (js/document.getElementById "root") @conn conn)
