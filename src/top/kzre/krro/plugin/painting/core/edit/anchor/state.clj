(ns top.kzre.krro.plugin.painting.core.edit.anchor.state
  (:require
    [top.kzre.krro.canvas.vector.path]
   [top.kzre.krro.canvas.vector.bezier.handle]
   [top.kzre.krro.core.reframe.transaction :as tx]
   [top.kzre.krro.plugin.painting.core.algo.segment]

   [top.kzre.krro.plugin.painting.core.edit.anchor.anchor-quadtree :as tree]
   [top.kzre.krro.plugin.painting.core.edit.anchor.overlay :as overlay]
   [top.kzre.krro.plugin.painting.core.edit.tool :as p]
   [top.kzre.krro.plugin.painting.core.transactions.anchor-extrude-modal :as anchor-extrude-modal]
   [top.kzre.krro.plugin.painting.core.transactions.anchor-fit-segment-free-modal
     :as anchor-fit-modal]
   [top.kzre.krro.plugin.painting.core.transactions.anchor-width-adjust-modal :as tx-width])
  (:import
    [java.util Set]
    (top.kzre.krro.canvas.vector.anchor Anchor)
    (top.kzre.krro.canvas.vector.bezier.handle Handle)
    (top.kzre.krro.canvas.vector.path Segment)))

;; ══════════════════════════════════════════════════════════════════════
;; 5. Record + 协议方法
;; ══════════════════════════════════════════════════════════════════════

(defrecord AnchorState
  [
   ;; 锚点
   ^Anchor active-anchor
   ^Set selected-anchors
   ^Anchor hover-anchor
   ;; 手柄
   ^Handle active-handle
   ^Set  selected-handles
   ^Handle hover-handle
   ;; 曲线段
   ^Segment hover-segment
   ^Set selected-paths]
  p/IToolData
  (dispatch-event [_ {:keys [type]} cofx]
    (when-let [tx (tx/current cofx)]
      ;; 不同事务下的事件派发
      (let [tx-kind (tx/kind tx)]
        (cond
          (and (= (anchor-extrude-modal/kind) tx-kind) (= type :move))
          :anchor/extrude-anchor

          (and (= (anchor-extrude-modal/kind) tx-kind) (= type :release))
          :anchor/exit-extrude-anchor-modal

          (and (= (anchor-fit-modal/kind) tx-kind) (= type :move))
          :anchor/fit-segment-free

          (and (= (anchor-fit-modal/kind) tx-kind) (= type :release))
          :anchor/exit-fit-segment-free-modal

          (and (= (tx-width/kind) tx-kind) (= type :move))
          :anchor/adjust-width

          (and (= (tx-width/kind) tx-kind) (= type :release))
          :anchor/exit-adjust-width-modal

          :else nil))))

  (cleanup! [_ ctx]
    (when-let [canvas-id (get-in ctx [:coeffects :record-id])]
      (tree/close canvas-id)))

  (overlay [this
            {:keys [layer-type layer-visible]
             :as tool-ctx}]
    (cond
      (not= :vector layer-type) nil
      (not layer-visible)       []
      :else
      (overlay/describe-overlay
        (overlay/make-context this tool-ctx)))))

(defn make-state
  [& {:keys [_mode] :or {}}]
  (map->AnchorState
    {:selected-anchors #{}
     :selected-handles #{}}))


(defn clear-selection
  "清空所有锚点选择状态"
  [state]
  (assoc state
    :active-anchor nil
    :selected-anchors #{}
    :active-handle nil
    :selected-handles #{}))

(defn selected-element-type [state]
  (cond
    (some? (:active-anchor state))
    :anchor
    (some? (:active-handle state))
    :handle
    :else nil))