(ns top.kzre.krro.plugin.painting.core.layer.undo.new-raster-layer
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.canvas.core.layer.util :as lu]
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.ops.snapshot :as snap]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.plugin.undo.core :as undo])
  (:import
    (top.kzre.krro.canvas.core.layer LayerUtils)
    (top.kzre.krro.util.tile TiledCanvas)))

(defn- undo-key [] ::new-raster-layer)

(defn record-new-raster-layer!
  [canvas-id layer-path
   {:keys [id
           ^TiledCanvas canvas]
    :as layer}]
  (let [tile-size (.getTileSize canvas)
        tiles (.getTiles canvas)
        wrapped   (snap/wrap-tiled-canvas canvas)
        layers (pc/layers-by-id canvas-id)
        layer-transform (lu/layer-transform layer-path layers)
        dirty-tiles (set (LayerUtils/transformTiles tiles tile-size layer-transform))]
    (undo/record-fx! (undo-key)
                     {:canvas-id     canvas-id
                      :layer-id      id
                      :path          layer-path
                      :dirty-tiles   dirty-tiles
                      :layer         (pc/persistable-layer layer)
                      :snapshot      wrapped})))

(undo/reg-undo-fx
  (undo-key)
  {:undo
   (fn [{:keys [old-metadata]}
        cb]
     (log/info "undo interceptor")
     (let [{:keys [canvas-id layer-id dirty-tiles]} old-metadata]
       (cb)
       (store/dispatch
         [:after-undo-add-raster-layer canvas-id layer-id dirty-tiles])))
   :redo
   (fn [{:keys [new-metadata]}
        cb]
     (log/info "redo interceptor")
     (let [{:keys [canvas-id layer-id dirty-tiles snapshot]} new-metadata]
       (let [canvas (snap/read-tiled-canvas snapshot)]
         (store/dispatch
           [:before-redo-add-raster-layer
            canvas-id layer-id canvas]))
       (cb)
       (store/dispatch
         [:after-redo-add-raster-layer
          canvas-id dirty-tiles])))})
