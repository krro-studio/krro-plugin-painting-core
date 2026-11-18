(ns top.kzre.krro.plugin.painting.core.oplog.anchor
  "锚点 oplog 事件。

   依赖锚点的挂表——本质还是路径改变。

   每个事件接收**操作意图**——内部调用 canvas.vector 领域函数算出结果——
   构造 change 派发到 :oplog/log。

   事件按关注点分组：
     几何   —— 锚点位置变化（translate / rotate / scale / mirror / skew）
     拓扑   —— 控制点数量 / 结构变化（insert / delete / extrude / weld）
     属性   —— 手柄 / 连续性 / 宽度

   所有事件产出的 change 都在 changes/path —— 不感知曲线类型。"
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.canvas.vector.core :as cv]
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.changes.path :as change]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [clojure.set :as set]))




;; 移动锚点
(rf/reg-event-fx
  store/app-id :oplog/anchor-translate
  (fn [cofx [_ canvas-id layer-id
             ;; map 参数会比 kwargs 更好，如果不是用于 dsl 的话
             {:keys [anchors dx dy paths]}
             ctx]]
    (let [{:keys [layer layer-transform]}
          (record/layer-context (:record cofx) layer-id)
          old-paths (or paths (cv/paths layer))
          new-paths (cv/translate-anchors old-paths anchors dx dy)
          op (apply change/make-vector-anchor-positions-changed
                    layer-id old-paths new-paths layer-transform anchors)]
      {:dispatch [:oplog/log canvas-id op ctx]})))

;; 挤出锚点
(rf/reg-event-fx
  store/app-id :oplog/anchor-extrude
  (fn [cofx [_ canvas-id layer-id end-anchor
             & {:keys [point]
                :as ctx}]]
    (let [{:keys [layer layer-transform]} (record/layer-context (:record cofx) layer-id)
          old-paths (cv/paths layer)
          new-point (or point (cv/anchor-point old-paths end-anchor))
          result    (cv/extrude-anchor old-paths end-anchor new-point)]
      (if result
        (let [{:keys [paths]} result
              path-id  (:path-id end-anchor)
              new-path (get paths path-id)
              op       (change/make-vector-paths-geometry-changed
                         layer-id old-paths layer-transform
                         :saved {path-id new-path})]
          {:dispatch [:oplog/log canvas-id op ctx]})
        (do
          (log/info "anchor-extrude: end-anchor is not an endpoint"
                    {:canvas-id  canvas-id
                     :layer-id   layer-id
                     :end-anchor end-anchor})
          nil)))))


;; ═══════════════════════════════════════════════
;; 几何 —— 锚点位置变化
;; ═══════════════════════════════════════════════

;; translate 已拆到 oplog.anchor-translate —— 拖动事务需要 :old-paths 提交基准。

(rf/reg-event-fx
  store/app-id :oplog/anchor-rotate
  (fn [cofx [_ canvas-id layer-id anchors center angle
             & {:keys [snap-opts falloff-opts pivot-opts]
                :as ctx}]]
    ;; TODO: 调 v/rotate-anchors → change/make-vector-anchor-positions-changed
    ))

(rf/reg-event-fx
  store/app-id :oplog/anchor-scale
  (fn [cofx [_ canvas-id layer-id anchors center sx sy
             & {:keys [snap-opts falloff-opts pivot-opts]
                :as ctx}]]
    ;; TODO: 调 v/scale-anchors → change/make-vector-anchor-positions-changed
    ))

(rf/reg-event-fx
  store/app-id :oplog/anchor-mirror
  (fn [cofx [_ canvas-id layer-id anchors axis
             & {:keys [snap-opts falloff-opts pivot-opts]
                :as ctx}]]
    ;; TODO: 调 v/mirror-anchors → change/make-vector-anchor-positions-changed
    ))

(rf/reg-event-fx
  store/app-id :oplog/anchor-skew
  (fn [cofx [_ canvas-id layer-id anchors center kx ky
             & {:keys [snap-opts falloff-opts pivot-opts]
                :as ctx}]]
    ;; TODO: 调 v/skew-anchors → change/make-vector-anchor-positions-changed
    ))

;; ═══════════════════════════════════════════════
;; 拓扑 —— 控制点数量 / 结构变化
;; ═══════════════════════════════════════════════

;; 插入锚点
(rf/reg-event-fx
  store/app-id :oplog/anchor-insert
  (fn [cofx [_ canvas-id layer-id
             {:keys [path-id t]}
             {:keys [] :as ctx}]]
    (let [{:keys [layer layer-transform]}
          (record/layer-context (:record cofx) layer-id)
          old-paths (cv/paths layer)
          new-paths
          (cv/insert-anchor old-paths path-id t)
          saved-paths (select-keys new-paths [path-id])
          op (change/make-vector-paths-geometry-changed
               layer-id old-paths layer-transform
               :deleted #{path-id}
               :saved  saved-paths)]
      {:dispatch [:oplog/log canvas-id op ctx]}
      )))


;; 删除锚点
(rf/reg-event-fx
  store/app-id :oplog/anchor-delete
  (fn [cofx [_ canvas-id layer-id anchors
             & {:keys [] :as ctx}]]
    (when (seq anchors)
      (let [{:keys [layer layer-transform]}
            (record/layer-context (:record cofx) layer-id)
            old-paths (cv/paths layer)
            new-paths
            (reduce
              (fn [acc anc]
                (cv/delete-anchor acc anc))
              old-paths
              anchors)
            path-ids (set (map :path-id anchors))
            saved-paths (select-keys new-paths path-ids)
            deleted-ids (set/difference
                          path-ids
                          (set (keys saved-paths)))
            op (change/make-vector-paths-geometry-changed
                 layer-id old-paths layer-transform
                 :deleted deleted-ids
                 :saved  saved-paths)]
        {:dispatch [:oplog/log canvas-id op ctx]}
        ))))

(rf/reg-event-fx
  store/app-id :oplog/anchor-weld
  (fn [cofx [_ canvas-id layer-id active-anchor passive-anchor & {:as ctx}]]
    ;; TODO: 调 v/weld-anchors → change/make-vector-anchor-positions-changed
    ;;                              或 change/make-vector-paths-geometry-changed（跨路径）
    ))
