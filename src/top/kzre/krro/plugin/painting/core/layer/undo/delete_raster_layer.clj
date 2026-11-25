(ns top.kzre.krro.plugin.painting.core.layer.undo.delete-raster-layer
  (:require
    [top.kzre.krro.canvas.core.layer.path :as layer.path]
    [top.kzre.krro.plugin.painting.core.layer.undo.delete-layer :as delete-layer]
    [top.kzre.krro.plugin.painting.core.ops.snapshot :as snap]
    [top.kzre.krro.plugin.painting.core.project.raster-layer :as pr])
  (:import
    (top.kzre.krro.util.tile TiledCanvas)))

(defrecord DeleteRasterLayerUndo []
  delete-layer/IDeleteLayerUndo

  (record-data [_ _canvas-id layers layer-path]
    (let [layer      (layer.path/get-layer layers layer-path)
          ^TiledCanvas canvas (:canvas layer)
          wrapped    (snap/wrap-tiled-canvas canvas)]
      ;; data 就是快照 + 恢复所需的所有信息
      {:layer-id    (:id layer)
       :snapshot    wrapped}))

  (undo-fx! [_ canvas-id data]
    ;; undo 重新创建raster
    (let [{:keys [layer-id snapshot]} data
          canvas (snap/read-tiled-canvas snapshot)]
      (pr/create-raster* layer-id canvas-id canvas)))

  (redo-fx! [_ _canvas-id data]
    ;; redo 重新删除 raster
    (let [{:keys [layer-id]} data]
      (pr/delete-raster! layer-id))))