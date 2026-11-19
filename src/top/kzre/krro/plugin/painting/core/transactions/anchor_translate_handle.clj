(ns top.kzre.krro.plugin.painting.core.transactions.anchor-translate-handle
  "手柄移动逻辑(TODO 约束检查）"
  (:require
    [top.kzre.krro.core.reframe.transaction :as tx]
    [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.transactions.vector-layer :as tx.vector-layer]))


(defonce ^:private kind* ::anchor-translate-handle)

(defn kind [] kind*)


(defrecord AnchorTranslateHandleTransaction
  [handles
   layer-backup
   init-point
   last-point
   changed?]
  tx/ITransaction
  (kind [_] (kind))

  (begin [_ {:keys [handles layer-event]} record]
    (let [{:keys [layer]} (record/layer-context record)
          p (select-keys layer-event [:x :y])]
      [(->AnchorTranslateHandleTransaction handles
                                     (clone/clone-layer layer)
                                     p p false)
       {:fx [[:tool/set-command-enabled false]]}]))

  (operate [this op-kind {:keys [layer-event]} record]
    (case op-kind
      :drag
      (let [{:keys [canvas-id layer-id]} (record/layer-context record)
            dx (- (:x layer-event) (:x last-point))
            dy (- (:y layer-event) (:y last-point))]
        (if (and (seq handles) (or (not (zero? dx)) (not (zero? dy))))
          [(assoc this
             :last-point (select-keys layer-event [:x :y])
             :changed? true)
           {:dispatch [:oplog/anchor-translate-handle canvas-id layer-id
                       {:handles handles
                        :dx dx
                        :dy dy}
                       {:undo? false}]}]
          [this {:fx []}]))
      (throw (ex-info "unknown op-kind for transaction anchor-translate"
                      {:op-kind op-kind}))))

  (commit [_ _ record]
    (let [{:keys [canvas-id layer-id]} (record/layer-context record)]
      (if (and changed? (seq handles))
        {:dispatch [:oplog/anchor-translate-handle canvas-id layer-id
                    {:handles handles :dx 0 :dy 0}
                    {:undo? true}]
         :fx [[:tool/set-command-enabled true]]}
        {:fx [[:tool/set-command-enabled true]]})))

  (rollback [_ _ record]
    (tx.vector-layer/rollback-effect record layer-backup)))

(tx/reg-transaction (kind)
                    (->AnchorTranslateHandleTransaction nil nil nil nil false))