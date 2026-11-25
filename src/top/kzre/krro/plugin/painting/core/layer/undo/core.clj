(ns top.kzre.krro.plugin.painting.core.layer.undo.core
  (:require
    [top.kzre.krro.plugin.painting.core.layer.undo.delete-layer :as delete-layer]
    [top.kzre.krro.plugin.painting.core.layer.undo.delete-raster-layer :as delete-raster-layer]))


(delete-layer/reg-undo
  :raster (delete-raster-layer/->DeleteRasterLayerUndo))

(delete-layer/reg-undo
  :vector (delete-layer/->DeletePersistentLayerUndo))

(delete-layer/reg-undo
  :group (delete-layer/->DeleteGroupUndo))