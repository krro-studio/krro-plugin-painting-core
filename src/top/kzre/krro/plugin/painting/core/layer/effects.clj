(ns top.kzre.krro.plugin.painting.core.layer.effects
  "副作用注册"
  (:require
   [top.kzre.krro.canvas.core.layer.core :as lc]
   [top.kzre.krro.core.reframe.core :as rf]
   [top.kzre.krro.plugin.painting.core.canvas.undo.edit-canvas :as undo.edit-canvas]
   [top.kzre.krro.plugin.painting.core.layer.undo.edit-raster-layer :as undo-edit-raster-layer]
   [top.kzre.krro.plugin.painting.core.layer.undo.new-raster-layer :as undo-new-raster-layer]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.core.project.raster-layer :as pr]
   [top.kzre.krro.plugin.painting.core.store :as store] ;; TODO deprecated
)
  (:import
   (top.kzre.krro.util.tile TiledCanvas)))

(rf/reg-fx
  store/app-id :save-raster-data
  (fn [_ canvas-id layer-id]
    (let [layer (pc/find-layer-in-canvas! canvas-id layer-id)
          canvas (:canvas layer)]
      (pr/create-raster! layer-id canvas-id canvas))))

(rf/reg-fx
  store/app-id :delete-raster-data
  (fn [_ layer-id]
    (pr/delete-raster! layer-id)))

(rf/reg-fx
  store/app-id :unchecked-create-raster-data
  (fn [_ layer-id canvas-id canvas]
    (pr/create-raster* layer-id canvas-id canvas)))

(rf/reg-fx
  store/app-id :record-raster-layer-added
  (fn [_ canvas-id layer-id]
    (let [layers (pc/layers-by-id! canvas-id)
          path (lc/find-layer-path layer-id layers)
          layer (lc/find-layer-by-path path layers)]
      (undo-new-raster-layer/record-new-raster-layer! canvas-id path layer))))

(rf/reg-fx
  store/app-id :record-raster-layer-edited
  (fn [_ canvas-id layer-id old-canvas new-canvas dirty-tiles layer-transform]
    (undo-edit-raster-layer/record-edit-raster-layer!
      canvas-id layer-id
      old-canvas new-canvas
      dirty-tiles layer-transform)
    (.close ^TiledCanvas old-canvas)
    (.close ^TiledCanvas new-canvas)))

(rf/reg-fx
  store/app-id :record-canvas-edited
  (fn [_ canvas-id]
    (undo.edit-canvas/record-edit-canvas!
      canvas-id)))

