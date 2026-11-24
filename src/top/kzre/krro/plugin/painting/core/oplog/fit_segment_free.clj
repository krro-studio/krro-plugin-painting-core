(ns top.kzre.krro.plugin.painting.core.oplog.fit-segment-free
  "单段自由拟合 oplog。

   拟合基准由 :paths 传入（模态给的是 begin 时的快照）——
   每次移动都从基准重算整段手柄，预览不累积误差。
   改的是段两侧锚点的手柄，锚点位置不动——attr-changed。"
  (:require
    [top.kzre.krro.canvas.vector.core :as cv]
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.changes.path :as change]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.store :as store]))

(rf/reg-event-fx
  store/app-id :oplog/fit-segment-free
  (fn [cofx [_ canvas-id layer-id path-id seg-idx point
             & {:keys [paths] :as ctx}]]
    (let [{:keys [layer layer-transform]} (record/layer-context (:record cofx) layer-id)
          old-paths  (cv/paths layer)
          base-paths (or paths old-paths)
          new-path   (cv/fit-segment-free (get base-paths path-id)
                                          seg-idx (:x point) (:y point))
          new-paths  (assoc base-paths path-id new-path)
          anchors    [(cv/->Anchor path-id seg-idx)
                      (cv/->Anchor path-id (inc seg-idx))]
          op (change/make-vector-anchor-positions-changed
                    layer-id old-paths new-paths anchors layer-transform)]
      {:dispatch [:oplog/log canvas-id op ctx]})))
