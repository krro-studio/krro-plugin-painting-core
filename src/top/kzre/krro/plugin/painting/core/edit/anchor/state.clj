(ns top.kzre.krro.plugin.painting.core.edit.anchor.state

  (:require
    [top.kzre.krro.canvas.core.layer.util :as util]
    [top.kzre.krro.canvas.vector.core :as cv]
    [top.kzre.krro.core.reframe.transaction :as tx]
    [top.kzre.krro.plugin.painting.core.algo.segment]
    [top.kzre.krro.plugin.painting.core.edit.anchor.anchor-quadtree :as tree]
    [top.kzre.krro.plugin.painting.core.edit.tool :as p]
    [top.kzre.krro.plugin.painting.core.transactions.anchor-extrude-modal :as anchor-extrude-modal]
    [top.kzre.krro.plugin.painting.core.transactions.anchor-fit-segment-free-modal
     :as anchor-fit-modal]
    [top.kzre.krro.plugin.painting.core.transactions.anchor-width-adjust-modal :as tx-width]
    [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import
    (top.kzre.krro.canvas.vector.anchor Anchor)))

;; ══════════════════════════════════════════════════════════════════════
;; 1. 数据适配层 —— 曲线 / anchor 结构差异收敛在这
;; ══════════════════════════════════════════════════════════════════════

(defn- anchor-pair
  "Anchor → [path-id point-idx]，用于 O(1) 查询。
   新状态若需要新的键（比如 [#anchor :handle-in]），在 point-state 里构造即可。"
  [^Anchor a]
  (when a [(:path-id a) (:point-idx a)]))

(defn- path-points
  "从 path 中取出锚点序列。适配点：
   - 现在只处理 :bezier；:catmull-rom 未来在这里补（两者 :curve 结构可能不同）。
   - 返回 vector 保证 idx 查询 O(1)。"
  [{:keys [path-type curve]}]
  (case path-type
    :bezier (vec (:points curve))
    nil))

(defn- control-offsets
  "从一个 anchor 点抽取入/出控制点偏移（相对锚点）。
   适配点：底层 curve 的 point 字段若与 overlay :curve 描述约定不同，
   只改这里。约定：[dx dy] 为零向量或无值时视为无手柄。"
  [point]
  (let [nz (fn [dx dy]
             (when (and (some? dx) (some? dy)
                        (or (not (zero? (double dx)))
                            (not (zero? (double dy)))))
               {:dx (double dx) :dy (double dy)}))]
    {:in  (nz (:dx1 point) (:dy1 point))   ;; 到达侧
     :out (nz (:dx2 point) (:dy2 point))})) ;; 出发侧

;; ══════════════════════════════════════════════════════════════════════
;; 2. 交互状态归约 —— 每帧一次，各层 O(1) 查询
;; ══════════════════════════════════════════════════════════════════════

(defn- interaction
  "把 record 上的瞬时字段归约成查询结构。
   新增交互状态（比如 :hover-handle、:locked-anchors）在这里加一条。"
  [{:keys [active-anchor second-active-anchor selected-anchors hover-anchor]}]
  {:active        (anchor-pair active-anchor)
   :second-active (anchor-pair second-active-anchor)
   :selected      (into #{} (map (juxt :path-id :point-idx)) selected-anchors)
   :hover         (anchor-pair hover-anchor)})

(defn- point-state
  "一个锚点当前该画成 overlay 的哪个 :state。
   优先级从上往下，这是唯一需要关心「状态如何叠加」的地方。

   注意 :second-active 在 overlay 的 anchor-colors 里没有独立分支，
   暂借 :selected 区分主次；若需要独立视觉，
   在 editor.core.overlay/anchor-colors 加一个分支即可。"
  [pair {:keys [active second-active selected hover]}]
  (cond
    (= pair active)           :active
    (= pair second-active)    :selected
    (contains? selected pair) :selected
    (= pair hover)            :hover
    :else                     :normal))

;; ══════════════════════════════════════════════════════════════════════
;; 3. 点上下文 —— 每点一次遍历，邻点随行（手柄/切线要相邻点，不重复扫）
;; ══════════════════════════════════════════════════════════════════════

(defn- enumerate-points
  [{:keys [paths path-order]}]
  (into []
        (mapcat
          (fn [pid]
            (let [pts (path-points (get paths pid))
                  n   (count pts)]
              (map-indexed
                (fn [i pt]
                  {:path-id pid
                   :idx     i
                   :point   pt
                   :prev    (when (pos? i) (nth pts (dec i)))
                   :next    (when (< (inc i) n) (nth pts (inc i)))})
                pts)))
          path-order)))

(defn- to-screen
  [ctx point]
  (let [{:keys [viewport layer-transform]} ctx
        w (util/transform-point layer-transform (:x point) (:y point))]
    (vp/logic->screen viewport (:x w) (:y w))))

;; ══════════════════════════════════════════════════════════════════════
;; 4. 绘制层 —— 每层签名 (fn [ctx point-context] -> seq of descs)
;;    产出纯 overlay 描述项，不碰几何细节之外的样式
;; ══════════════════════════════════════════════════════════════════════

(defn- tangent-descs
  "切线：active 锚点到相邻锚点的方向线。
   想让切线常显 / 换色，只改这里（或换成 [:curve ...] 由下层统一样式）。"
  [ctx {:keys [path-id idx point prev next]}]
  (when (= [path-id idx] (:active (:interaction ctx)))
    (let [base (to-screen ctx point)
          seg  (fn [neighbor]
                 (when neighbor
                   (let [s (to-screen ctx neighbor)]
                     [:line {:x1 (:x base) :y1 (:y base)
                             :x2 (:x s)    :y2 (:y s)
                             :stroke-width 1.0}])))]
      (cond-> []
              prev (conj (seg prev))
              next (conj (seg next))))))

(defn- handle-visible?
  "手柄显隐策略集中在这。
   默认只在 active / second-active 上显示；要扩展（比如选中时也显示），改这一处。"
  [pair {:keys [active second-active]}]
  (or (= pair active) (= pair second-active)))

(defn- handle-descs
  "贝塞尔控制手柄：锚点 → 入/出控制点的引线 + 小圆。
   使用 overlay 的 :handle 描述项，端点样式 (:in/:out 差异) 由下层处理。"
  [ctx {:keys [path-id idx point]}]
  (when (handle-visible? [path-id idx] (:interaction ctx))
    (let [base (:point ctx) ; 占位，见下方修正
          {:keys [in out]} (control-offsets point)
          mk (fn [{:keys [dx dy] :as offset} state]
               (when offset
                 (let [a (to-screen ctx point)
                       h (to-screen ctx {:x (+ (:x point) dx)
                                         :y (+ (:y point) dy)})]
                   [:handle {:x (:x a) :y (:y a)
                             :hx (:x h) :hy (:y h)
                             :state state}])))]
      (cond-> []
              in  (conj (mk in  :in))
              out (conj (mk out :out))))))

(defn- anchor-descs
  "锚点本体。所有样式交给 overlay :anchor 的状态 palette。"
  [ctx {:keys [path-id idx point]}]
  (let [st (point-state [path-id idx] (:interaction ctx))
        s  (to-screen ctx point)]
    [[:anchor {:x (:x s) :y (:y s) :state st :shape :square}]]))

(def ^:private point-layers
  "每个锚点上、从底到顶依次执行的绘制层。
   加新元素（相邻高亮、命中提示、宽度手柄……）只在这里加一个 fn。"
  [tangent-descs
   handle-descs
   anchor-descs])

(defn- render-points [{:keys [points] :as ctx}]
  (mapcat (fn [p]
            (mapcat #(% ctx p) point-layers))
          points))

;; ══════════════════════════════════════════════════════════════════════
;; 5. Record + 协议方法
;; ══════════════════════════════════════════════════════════════════════

(defrecord AnchorState
  [^Anchor active-anchor
   ^Anchor second-active-anchor
   selected-anchors
   selected-paths
   ^Anchor hover-anchor
   last-layer-point]
  p/IToolData
  (dispatch-event [_ {:keys [type]} cofx]
    (when-let [tx (tx/current cofx)]
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

  (overlay [this {:keys [viewport layer layer-type layer-visible layer-transform]}]
    (cond
      (not= :vector layer-type) nil
      (not layer-visible)       []
      :else
      (let [paths      (cv/paths layer)
            path-order (cv/path-order layer)]
        (when (seq path-order)
          (render-points
            {:interaction     (interaction this)
             :viewport        viewport
             :layer-transform layer-transform
             :points          (enumerate-points {:paths      paths
                                                 :path-order path-order})}))))))

(defn make-state
  [& {:keys [_mode] :or {}}]
  (map->AnchorState {}))


(defn clear-selected-anchors
  "清空所有锚点选择状态"
  [state]
  (assoc state
    :active-anchor nil
    :selected-anchors #{}))