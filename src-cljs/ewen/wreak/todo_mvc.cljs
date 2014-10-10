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
                   [cljs.core.match.macros :refer [match]]))


(defn set-attr! [conn id attr val]
  (ds/transact! conn [{:db/id id
                      attr   val}]))

(defn get-by-attr [db attr]
  (ds/q '[:find ?id
          :in $ ?attr
          :where [?id ?attr]]
        db attr))

(defn only
  "Return the only item from a query result"
  ([query-result]
   (assert (= 1 (count query-result)))
   (assert (= 1 (count (first query-result))))
   (ffirst query-result))
  ([query-result default]
   (if (= 0 (count query-result)) default
                                  (only query-result))))




#_(defn todo-input-stop [])

(defn add-todo! [conn text]
  (ds/transact! conn [{:db/id -1
                      :todo-item/title text
                      :todo-item/done false}]))

(def todo-input
  (component "todo-input"
             (mixin {:render          (fn [_ {:keys [title]}]
                                        (let [conn (w/get-conn *component*)
                                              id (aget *component* :ewen.wreak/id)]
                                          (html [:input#new-todo
                                                 {:type        "text"
                                                  :val         title
                                                  :placeholder "What needs to be done?"
                                                  :on-change   #(set-attr! conn id :new-todo/val (-> % .-target .-value))
                                                  :on-key-up   #(case (.-which %)
                                                                 13 (let [v (-> title str clojure.string/trim)]
                                                                      (if-not (empty? v) (add-todo! conn v))
                                                                      #_(todo-input-stop))
                                                                 27 (set-attr! conn id :new-todo/val "")
                                                                 nil)}])))
                     :getInitialState (fn [_ db]
                                        (let [id (aget *component* :ewen.wreak/id)]
                                          {:title (-> (ds/entity db id) :new-todo/val)}))
                     :dbDidUpdate     (fn [_ state {:keys [db-after]}]
                                        (let [id (aget *component* :ewen.wreak/id)]
                                          (assoc state
                                            :title
                                            (-> (ds/entity db-after id) :new-todo/val))))}
                    (w/component-id-mixin "todo-input"))))

(comment
  (defn todo-item []
    (let [editing (atom false)]
      (fn [{:keys [id done title]}]
        [:li {:class (str (if done "completed ")
                          (if @editing "editing"))}
         [:div.view
          [:input.toggle {:type      "checkbox" :checked done
                          :on-change #(toggle id)}]
          [:label {:on-double-click #(reset! editing true)} title]
          [:button.destroy {:on-click #(delete id)}]]
         (when @editing
           [todo-edit {:class   "edit" :title title
                       :on-save #(save id %)
                       :on-stop #(reset! editing false)}])]))))

#_(def todo-edit (with-meta todo-input
                          {:component-did-mount #(.focus (reagent/dom-node %))}))

(def todo-edit
  todo-input)

(defn toggle [db id attr]
  (let [val (-> (ds/entity db id) attr)]
    [{:db/id id
      attr (not val)}]))

(defn toggle-done [conn id]
  (ds/transact! conn [[:db.fn/call toggle id :todo-item/done]]))

(defn delete [conn id]
  (ds/transact! conn [[:db.fn/retractEntity id]]))

(def todo-item
  (component "todo-item"
             {:render (fn [_ {:keys [id done editing title]}]
                        (let [conn (w/get-conn *component*)]
                          (html [:li {:class (str (if done "completed ")
                                                  (if editing "editing"))}
                                 [:div.view
                                  [:input.toggle {:type      "checkbox" :checked done
                                                  :on-change #(toggle-done conn id)}]
                                  [:label {:on-double-click #(set-attr! conn id :todo-item/editing true)} title]
                                  [:button.destroy {:on-click #(delete conn id)}]]
                                 (when editing
                                   (todo-edit {:class   "edit" :title title
                                               #_:on-save #_#(save id %)
                                               #_:on-stop #_#(reset! editing false)}))])))
              :componentWillMount (fn [{:keys [id]} _]
                                    (ds/transact! (w/get-conn *component*)
                                                  [{:db/id id
                                                   :todo-item/editing false}]))
              :getInitialState (fn [{:keys [id]} db]
                                 (let [ent (ds/entity db id)]
                                   {:id (:db/id ent)
                                   :title (:todo-item/title ent)
                                   :done (:todo-item/done ent)
                                   :editing (:todo-item/editing ent)}))
              :dbDidUpdate     (fn [{:keys [id]} _ {:keys [db-after]}]
                                 (let [ent (ds/entity db-after id)]
                                   {:id (:db/id ent)
                                    :title (:todo-item/title ent)
                                    :done (:todo-item/done ent)
                                    :editing (:todo-item/editing ent)}))}))



(comment
  (defn todo-app [props]
    (let [filt (atom :all)]
      (fn []
        (let [items (vals @todos)
              done (->> items (filter :done) count)
              active (- (count items) done)]
          [:div
           [:section#todoapp
            [:header#header
             [:h1 "todos"]
             [todo-input {:id          "new-todo"
                          :placeholder "What needs to be done?"
                          :on-save     add-todo}]]
            (when (-> items count pos?)
              [:div
               [:section#main
                [:input#toggle-all {:type      "checkbox" :checked (zero? active)
                                    :on-change #(complete-all (pos? active))}]
                [:label {:for "toggle-all"} "Mark all as complete"]
                [:ul#todo-list
                 (for [todo (filter (case @filt
                                      :active (complement :done)
                                      :done :done
                                      :all identity) items)]
                   ^{:key (:id todo)} [todo-item todo])]]
               [:footer#footer
                [todo-stats {:active active :done done :filt filt}]]])]
           [:footer#info
            [:p "Double-click to edit a todo"]]])))))

(defn get-todos [db]
  (->> (ds/q '[:find ?id ?title ?done
               :where [?id :todo-item/title ?title]
               [?id :todo-item/done ?done]]
             db)
       (reduce (fn [items [id title done]]
                 (assoc items id {:id id :title title :done done}))
               {})))


(def todo-app
  (component "todo-app"
               {:render (fn [_ todos]
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
                                        [:input#toggle-all #_{:type      "checkbox" :checked (zero? active)
                                                            :on-change #(complete-all (pos? active))}]
                                        [:label {:for "toggle-all"} "Mark all as complete"]
                                        [:ul#todo-list
                                         (for [todo (filter (case :all #_@filt
                                                              :active (complement :done)
                                                              :done :done
                                                              :all identity) items)]
                                           (todo-item {:key (:id todo)
                                                       :id (:id todo)}))]]
                                       [:footer#footer
                                        #_[todo-stats {:active active :done done :filt filt}]]])]
                                   [:footer#info
                                    [:p "Double-click to edit a todo"]]])))
                :getInitialState (fn [_ db]
                                   (get-todos db))
                :dbDidUpdate     (fn [_ _ {:keys [db-after]}]
                                   (get-todos db-after))}))

(defn load-app []
  (let [conn (ds/create-conn)]
    (ds/transact! conn [{:db/id -1
                        :todo-item/title "e"
                        :todo-item/done false}])
    conn))

(def conn (load-app))

(w/render todo-app nil (.-body js/document) @conn conn)


