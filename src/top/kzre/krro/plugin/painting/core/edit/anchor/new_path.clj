(ns top.kzre.krro.plugin.painting.core.edit.anchor.new-path
  (:require
   [top.kzre.krro.canvas.vector.anchor :as anchor]
   [top.kzre.krro.core.reframe.core :as rf]
   [top.kzre.krro.core.reframe.transaction :refer [transaction-interceptor]]
   [top.kzre.krro.plugin.painting.core.edit.anchor.anchor-quadtree :refer [anchor-quadtree-interceptor]]
   [top.kzre.krro.plugin.painting.core.edit.anchor.state :as anchor.state]
   [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [get-tool-context
                                                                 tool-context-interceptor
                                                                 update-tool-context]]
   [top.kzre.krro.plugin.painting.core.edit.path :as edit.path]
   [top.kzre.krro.plugin.painting.core.edit.tool :as tool :refer [cleanup-tool-interceptor]]
   [top.kzre.krro.plugin.painting.core.store :as store])
  (:import
   (top.kzre.krro.plugin.painting.core.edit.anchor.state AnchorState)))


(rf/reg-event-fx
  store/app-id :anchor-new-path/bezier
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-state)))
   (tool-context-interceptor)
   (anchor-quadtree-interceptor)
   (transaction-interceptor)]
  (fn [cofx [_ record-id _ frame]]
    (let [{:keys [layer-id layer-type layer-event transaction-kind]} (get-tool-context cofx)]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}

        (some? transaction-kind)
        {:fx [[:warn "Transaction already exists!"]]}

        :else
        (let [path-id (keyword (str "path-" (random-uuid)))
              path (edit.path/make-bezier-path
                     (select-keys layer-event [:x :y]))
              anchor (anchor/->Anchor path-id 0)
              record (:record cofx)
              ctx (get-tool-context cofx)
              new-record
              (-> record
                  ;; 自动进入选择并激活
                  (assoc-in [:tool-data :active-anchor] anchor)
                  (assoc-in [:tool-data :selected-anchors] #{anchor}))]
          {:record new-record
           :dispatch
           [:oplog/vector-path-saved
            record-id layer-id path-id path
            {:undo true
             :effects
             (fn [final-record]
               (when-let [final-tool-data (get final-record :tool-data)]
                 (let [final-ctx (update-tool-context ctx final-record)]
                   {:fx
                    [[:tool/flush-overlay
                      (tool/overlay final-tool-data final-ctx)
                      frame]]} )))}]
           })))))