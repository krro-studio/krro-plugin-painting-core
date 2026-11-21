(ns top.kzre.krro.plugin.painting.core.edit.anchor.anchor
  "锚点选择+直接拖动事件"
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.canvas.vector.anchor]
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.core.reframe.transaction :as tx :refer [transaction-interceptor]]
    [top.kzre.krro.plugin.painting.core.edit.anchor.anchor-quadtree :as tree :refer [anchor-quadtree-interceptor]]
    [top.kzre.krro.plugin.painting.core.edit.anchor.state :as anchor.state]
    [top.kzre.krro.plugin.painting.core.edit.common :as common]
    [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [tool-context-interceptor]]
    [top.kzre.krro.canvas.vector.bezier.handle]
    [top.kzre.krro.plugin.painting.core.edit.tool :as tool :refer [cleanup-tool-interceptor]]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.plugin.painting.core.transactions.anchor-translate :as tx-translate-anchor]
    [top.kzre.krro.plugin.painting.core.transactions.anchor-translate-handle :as tx-translate-handle])
  (:import
    (top.kzre.krro.canvas.core QuadTree QuadTree$NearestResult)
    (top.kzre.krro.canvas.vector.anchor Anchor)
    (top.kzre.krro.canvas.vector.bezier.handle Handle)
    (top.kzre.krro.plugin.painting.core.edit.anchor.state AnchorState)))

;; press 无法判定本次手势是选择还是位移，保持空逻辑。
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

          record (:record cofx)
          tool-data (get record :tool-data)
          el-type (anchor.state/selected-element-type tool-data)
          tx-kind
          (case el-type
            :anchor (tx-translate-anchor/kind)
            :handle (tx-translate-handle/kind)
            nil)]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}

        ;; 位移事务尚未开始——本帧只备份图层并定下起点，不动几何
        (nil? transaction-kind)
        (cond
          ;; 拖动锚点
          (= el-type :anchor)
          (let [anchors (vec (get-in record [:tool-data :selected-anchors]))]
            (when (seq anchors)
              {:transaction
               (tx/tx-begin (tx-translate-anchor/kind)
                            {:anchors anchors
                             :layer-event layer-event})}))
          ;; 拖动手柄
          (= el-type :handle)
          (let [handles (vec (get-in record [:tool-data :selected-handles]))]
            (when (seq handles)
              {:transaction
               (tx/tx-begin (tx-translate-handle/kind)
                            {:handles handles
                             :layer-event layer-event})}))

          ;; TODO segment 的立即拟合拖动
          :else nil)

        ;; 锚点, 手柄的逻辑参数都是一致的
        (= transaction-kind tx-kind)
        {:transaction
         (tx/tx-operate
           tx-kind :drag
           {:layer-event layer-event})
         :fx [[:tool/flush-overlay
               (tool/overlay tool-data ctx)
               frame]]}))))

(rf/reg-event-fx
  store/app-id :anchor-translate/release
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-state)))
   (tool-context-interceptor)
   (anchor-quadtree-interceptor)
   (transaction-interceptor)]
  (fn [cofx [_ record-id event-map frame]]
    (let [ctx (:krro.painting/tool-context cofx)
          {:keys [click-count layer-event layer-type layer-transform viewport
                  transaction-kind]} ctx
          record (:record cofx)
          tool-data (get record :tool-data)
          el-type (anchor.state/selected-element-type tool-data)
          tx-kind
          (case el-type
            :anchor (tx-translate-anchor/kind)
            :handle (tx-translate-handle/kind)
            nil)]
      (cond
        ;; 图层非法
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for" layer-type)]]}

        ;; 拖动事务提交
        (and
          (some? tx-kind)
          (= transaction-kind tx-kind))
        {:transaction
         (tx/tx-commit tx-kind {})
         :fx [[:tool/flush-overlay (tool/overlay tool-data ctx) frame]]}

        ;; 其他事务中，忽略
        (some? transaction-kind)
        nil

        ;; 单击选择 (顶点/手柄)
        (= 1 click-count)
        (let [record (:record cofx)
              tool-data (get-in record [:tool-data])

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
            (let [closest (.-value result)
                  el-type (anchor.state/selected-element-type tool-data)]
              (cond
                ;; 选择锚点
                (and (instance? Anchor closest)
                     (or ;; 不是多选，或者没有跨元素选
                       (not multi-select?)
                       (or (nil? el-type)
                           (= el-type :anchor))))
                (let [selected (:selected-anchors tool-data #{})
                      new-selected (if multi-select?
                                     (conj selected closest)
                                     #{closest})
                      new-tool-data (assoc tool-data
                                      :selected-anchors new-selected
                                      :active-anchor closest
                                      :selected-handles #{}
                                      :active-handle nil)]
                  {:record (assoc-in record [:tool-data] new-tool-data)
                   :fx [[:tool/flush-overlay (tool/overlay new-tool-data ctx) frame]]})

                ;; 选择手柄
                (and (instance? Handle closest)
                     (or (not multi-select?)
                         (or (nil? el-type)
                             (= el-type :handle))))
                (let [selected (:selected-handles tool-data #{})
                      new-selected (if multi-select?
                                     (conj selected closest)
                                     #{closest})
                      new-tool-data (assoc tool-data
                                      :selected-handles new-selected
                                      :active-handle closest
                                      :selected-anchors #{}
                                      :active-anchor nil)]
                  (log/info "select handle: " new-selected)
                  {:record (assoc record :tool-data new-tool-data)
                   :fx [[:tool/flush-overlay (tool/overlay new-tool-data ctx) frame]]})
                ;; 跨元素选择
                :else {:fx [[:warn "Cannot selected anchor and handle in a selection."]]}))

            ;; 没选中
            (if multi-select?
              ;; 加选保持
              {:fx [[:tool/flush-overlay (tool/overlay tool-data ctx) frame]]}
              ;; 否则清空选择
              (let [new-tool-data (anchor.state/clear-selection tool-data)]
                {:record (assoc record :tool-data new-tool-data)
                 :fx [[:tool/flush-overlay (tool/overlay new-tool-data ctx) frame]]}))))

        ;; 未知情况
        :else nil))))
