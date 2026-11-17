(ns top.kzre.krro.plugin.painting.core.transactions.anchor-translate
  "锚点移动事务。

   drag 时以增量 dx/dy 派发 :oplog/anchor-translate（undo? false）做实时预览；
   commit 时以 layer-backup 为基准再派发一次完整位移（undo? true）合并为单一 undo 单元。
   选择（selected-anchors / active-anchor）是 UI 状态，不进入本事务。"
  (:require
    [top.kzre.krro.core.reframe.transaction :as tx]
    [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
    [top.kzre.krro.plugin.painting.core.project.vector-layer :as pv]
    [top.kzre.krro.plugin.painting.core.record :as record]))

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
                       anchors dx dy :undo? false]}]
          [this {:fx []}]))
      (throw (ex-info "unknown op-kind for transaction anchor-translate"
                      {:op-kind op-kind}))))

  (commit [_ _ record]
    (let [{:keys [canvas-id layer-id]} (record/layer-context record)
          dx (- (:x last-point) (:x init-point))
          dy (- (:y last-point) (:y init-point))]
      (if (and changed? (seq anchors) (or (not (zero? dx)) (not (zero? dy))))
        {:dispatch [:oplog/anchor-translate canvas-id layer-id
                    anchors dx dy
                    :old-paths (pv/paths layer-backup)
                    :undo? true]
         :fx [[:tool/set-command-enabled true]]}
        {:fx [[:tool/set-command-enabled true]]})))

  (rollback [_ _ record]
    (let [{:keys [canvas-id layer-id layer]} (record/layer-context record)]
      {:dispatch [:oplog/vector-layer-paths-dirty
                  canvas-id layer-id
                  (pv/paths layer)
                  (pv/paths layer-backup)
                  :undo? false]
       :fx [[:tool/set-command-enabled true]]})))

(tx/reg-transaction (kind)
                    (->AnchorTranslateTransaction nil nil nil nil false))