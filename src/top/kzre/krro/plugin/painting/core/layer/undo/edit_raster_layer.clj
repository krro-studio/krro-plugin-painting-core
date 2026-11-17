(ns top.kzre.krro.plugin.painting.core.layer.undo.edit-raster-layer
  "光栅图层的光栅数据得到编辑"
  (:require
    [top.kzre.krro.canvas.core.layer.core :as layer-core]
    [top.kzre.krro.core.message :as msg]
    [top.kzre.krro.core.reframe.reframe :as rf]
    [top.kzre.krro.plugin.painting.core.ops.snapshot :as snap]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.plugin.undo.core :as undo])
  (:import
    (java.util Collection)
    (top.kzre.krro.util.tile TiledCanvas)))

(defn undo-key [] ::edit-raster-layer)

(defn record-edit-raster-layer!
  "构造光栅笔触撤销元数据，现保存完整画布快照（全量）。"
  [canvas-id layer-id
   ^TiledCanvas old-canvas ^TiledCanvas new-canvas
   dirty-tiles layer-transform]
  (undo/record-fx!
    (undo-key)
    {:canvas-id     canvas-id
     :layer-id      layer-id
     :dirty-tiles   (set dirty-tiles)
     :layer-transform layer-transform
     :old-snapshot  (snap/wrap-tiled-canvas old-canvas dirty-tiles)
     :new-snapshot  (snap/wrap-tiled-canvas new-canvas dirty-tiles)}))


(defn- restore-tiles
  "从快照恢复光栅笔触。将脏瓦片合并到当前画布中，并强制刷新。"
  [{:keys [canvas-id layer-id dirty-tiles]} snapshot]
  (let [^TiledCanvas dirty-canvas (snap/read-tiled-canvas snapshot)
        cd          (pc/canvas-data! canvas-id)
        layer       (layer-core/find-layer layer-id (:layers cd))]
    (if-not layer
      (msg/error (str "Layer not found for restore: " layer-id))
      (let [^TiledCanvas current-canvas (:canvas layer)]
        (.deleteTiles current-canvas ^Collection dirty-tiles)
        (.mergeCanvas current-canvas dirty-canvas)
        (rf/dispatch store/app-id
                     [:refresh-canvas canvas-id]
                     ;; TODO 利用layer-transform 只更新局部数据，而不是全部刷新
                     ;[:render-canvas canvas-id (viewport/refreshed)]
                     )))
    (.clear dirty-canvas)))


(undo/reg-undo-fx
  (undo-key)
  {:undo
   (fn [{:keys [old-metadata]}
        cb]
     (cb)
     (restore-tiles old-metadata (:old-snapshot old-metadata)))
   :redo
   (fn [{:keys [new-metadata]}
        cb]
     (cb)
     (restore-tiles new-metadata (:new-snapshot new-metadata)))})