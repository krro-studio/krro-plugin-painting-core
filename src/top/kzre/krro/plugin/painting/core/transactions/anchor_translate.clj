(ns top.kzre.krro.plugin.painting.core.transactions.anchor-translate
  "锚点移动事务。

   drag 首帧由编辑层起事务：begin 只备份图层并定下 init-point，不改几何；
   后续 drag 帧以增量 dx/dy 派发 :oplog/anchor-translate（undo? false）做实时预览；
   commit 时以 layer-backup 为基准再派发一次完整位移（undo? true）合并为单一 undo 单元。
   选择（selected-anchors / active-anchor）是 UI 状态，不进入本事务。"
  (:require
    [top.kzre.krro.canvas.vector.core :as cv]
    [top.kzre.krro.core.reframe.transaction :as tx]
    [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.transactions.vector-layer :as tx.vector-layer]))

(defonce ^:private kind* ::anchor-translate)

(defn kind [] kind*)

(defrecord AnchorTranslateTransaction
  [anchors
   layer-backup
   init-point
   last-point
   changed?]
  tx/ITransaction
  (kind [_] (kind))

  (begin [_ {:keys [anchors layer-event]} record]
    (let [{:keys [layer]} (record/layer-context record)
          p (select-keys layer-event [:x :y])]
      [(->AnchorTranslateTransaction anchors
                                     (clone/clone-layer layer)
                                     p p false)
       {:fx [[:tool/set-command-enabled false]]}]))

  (operate [this op-kind {:keys [layer-event]} record]
    (case op-kind
      :drag
      (let [{:keys [canvas-id layer-id]} (record/layer-context record)
            dx (- (:x layer-event) (:x last-point))
            dy (- (:y layer-event) (:y last-point))]
        (if (and (seq anchors) (or (not (zero? dx)) (not (zero? dy))))
          [(assoc this
             :last-point (select-keys layer-event [:x :y])
             :changed? true)
           {:dispatch [:oplog/anchor-translate canvas-id layer-id
                       {:anchors anchors
                        :dx dx
                        :dy dy}
                       {:undo? false}]}]
          [this {:fx []}]))
      (throw (ex-info "unknown op-kind for transaction anchor-translate"
                      {:op-kind op-kind}))))

  (commit [_ _ record]
    (let [{:keys [canvas-id layer-id]} (record/layer-context record)
          dx (- (:x last-point) (:x init-point))
          dy (- (:y last-point) (:y init-point))]
      (if (and changed? (seq anchors) (or (not (zero? dx)) (not (zero? dy))))
        ;; old-paths 取 layer-backup——基准是拖动前的 paths，位移结果是绝对值，
        ;; 落到 paths 上与预览增量同值，不会叠加。
        ;; undo? true 让 op 进入 undo，一次拖动 = 一个 undo 单元。
        {:dispatch [:oplog/anchor-translate canvas-id layer-id
                    {:anchors anchors
                     :dx dx
                     :dy dy
                     :paths (cv/paths layer-backup)}
                    {:undo? true}]
         :fx [[:tool/set-command-enabled true]]}
        {:fx [[:tool/set-command-enabled true]]})))

  (rollback [_ _ record]
    (tx.vector-layer/rollback-effect record layer-backup)))

(tx/reg-transaction (kind)
                    (->AnchorTranslateTransaction nil nil nil nil false))
