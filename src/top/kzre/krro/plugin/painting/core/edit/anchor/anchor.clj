(ns top.kzre.krro.plugin.painting.core.edit.anchor.anchor
  (:require
   [top.kzre.krro.canvas.vector.anchor]
   [top.kzre.krro.core.reframe.core :as rf]
   [top.kzre.krro.core.reframe.transaction :as tx :refer [transaction-interceptor]]
   [top.kzre.krro.plugin.painting.core.edit.anchor.anchor-quadtree :as tree :refer [anchor-quadtree-interceptor]]
   [top.kzre.krro.plugin.painting.core.edit.anchor.state :as anchor.state]
   [top.kzre.krro.plugin.painting.core.edit.common :as common]
   [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [tool-context-interceptor]]
   [top.kzre.krro.plugin.painting.core.edit.tool :as tool :refer [cleanup-tool-interceptor]]
   [top.kzre.krro.plugin.painting.core.store :as store]
   [top.kzre.krro.plugin.painting.core.transactions.anchor-translate :as tx-anchor-translate])
  (:import
    (top.kzre.krro.canvas.core QuadTree QuadTree$NearestResult)
    (top.kzre.krro.canvas.vector.anchor Anchor)
    (top.kzre.krro.plugin.painting.core.edit.anchor.state AnchorState)))

;; press 无法判定本次手势是选择还是位移，保持空逻辑。
;; drag 首帧（无事务）起事务并把本帧作为 init-point，后续帧执行事务操作，
;; release 提交事务打 undo 标记。选择等 UI 状态留在本 ns，
;; 几何改动与 quadtree 维护由事务经 oplog 完成。

(rf/reg-event-fx
  store/app-id :anchor-translate/press
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-state)))
   (tool-context-interceptor)]
  (fn [_ _] nil))

(rf/reg-event-fx
  store/app-id :anchor-translate/drag
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-state)))
   (tool-context-interceptor)
   (anchor-quadtree-interceptor)
   (transaction-interceptor)]
  (fn [cofx [_ _ _ frame]]
    (let [ctx (:krro.painting/tool-context cofx)
          {:keys [layer-event layer-type transaction-kind]} ctx
          tx-kind (tx-anchor-translate/kind)]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}

        ;; 位移事务尚未开始——本帧只备份图层并定下起点，不动几何
        (nil? transaction-kind)
        (let [anchors (vec (get-in (:record cofx) [:tool-data :selected-anchors]))]
          (when (seq anchors)
            {:transaction [(tx/begin-transaction tx-kind
                               :anchors anchors
                               :layer-event layer-event)]}))

        (= transaction-kind tx-kind)
        {:transaction [(tx/transaction-operation tx-kind :drag
                            :layer-event layer-event)]
         :fx [[:tool/flush-overlay
               (tool/overlay (get-in (:record cofx) [:tool-data]) ctx)
               frame]]}))))

;; TODO 这里加速结构维护失败
(rf/reg-event-fx
  store/app-id :anchor-translate/release
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-state)))
   (tool-context-interceptor)
   (anchor-quadtree-interceptor)
   (transaction-interceptor)]
  (fn [cofx [_ record-id event-map frame]]
    (let [ctx (:krro.painting/tool-context cofx)
          {:keys [click-count layer-event layer-type layer-transform viewport
                  transaction-kind]} ctx]
      (cond
        ;; 图层非法
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for" layer-type)]]}

        ;; 单锚点拖动事务提交
        (= transaction-kind (tx-anchor-translate/kind))
        (let [record (:record cofx)
              tool-data (get-in record [:tool-data])]
          {:fx [[:tool/flush-overlay (tool/overlay tool-data ctx) frame]]})

        ;; 其他事务中，忽略
        (some? transaction-kind)
        nil

        ;; 单击选择 (顶点/手柄)
        (= 1 click-count)
        (let [record (:record cofx)
              tool-data (get-in record [:tool-data])
              selected-set (:selected-anchors tool-data #{})
              multi-select? (get-in event-map [:modifiers :shift] false)
              threshold 10.0
              ^QuadTree tree (tree/tree record-id)
              ^QuadTree$NearestResult result (.nearest tree (:x layer-event) (:y layer-event))]
          (if (and result
                   (let [screen-pos (common/layer-point->screen
                                      {:x (.-x result)
                                       :y (.-y result)}
                                      layer-transform viewport)
                         dist (Math/hypot (- (:x screen-pos) (:x event-map))
                                          (- (:y screen-pos) (:y event-map)))]
                     (< dist threshold)))
            (let [closest (.-value result)]
              (cond
                (instance? Anchor closest)
                (let [closest (.-value result)
                      new-selected (if multi-select?
                                     (conj selected-set closest)
                                     #{closest})
                      new-tool-data (assoc tool-data
                                      :selected-anchors new-selected
                                      :active-anchor closest)]
                  {:record (assoc-in record [:tool-data] new-tool-data)
                   :fx [[:tool/flush-overlay (tool/overlay new-tool-data ctx) frame]]})
                ))

            ;; 没选中
            (if multi-select?
              ;; 加选保持
              {:fx [[:tool/flush-overlay (tool/overlay tool-data ctx) frame]]}
              ;; 否则清空选择
              (let [new-tool-data (assoc tool-data
                                    :selected-anchors #{}
                                    :active-anchor nil
                                    ;:active-handle nil
                                    ;:selected-handles nil
                                    )]
                {:record (assoc-in record [:tool-data] new-tool-data)
                 :fx [[:tool/flush-overlay (tool/overlay new-tool-data ctx) frame]]}))))

        ;; 位置情况
        :else nil))))
