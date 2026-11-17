(ns top.kzre.krro.plugin.painting.core.oplog.anchor-translate
  "锚点位移 oplog。

   preview（undo? false）用 record 当前 paths 作 old-paths；
   commit（undo? true）由事务传入 :old-paths（layer-backup 的 paths）作基准。
   生成的 VectorAnchorPositionsChanged 在 realize 时自动更新 quadtree 并渲染。"
  (:require
    [top.kzre.krro.canvas.vector.core :as cv]
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.changes.path :as change]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.store :as store]))

(rf/reg-event-fx
  store/app-id :oplog/anchor-translate
  (fn [cofx [_ canvas-id layer-id anchors dx dy
             & {:keys [old-paths] :as ctx}]]
    (let [{:keys [layer layer-transform]}
          (record/layer-context (:record cofx) layer-id)
          old-paths (or old-paths (cv/paths layer))
          new-paths (cv/translate-anchors old-paths anchors dx dy)
          op (apply change/make-vector-anchor-positions-changed
                    layer-id old-paths new-paths layer-transform anchors)]
      {:dispatch [:oplog/log canvas-id op ctx]})))