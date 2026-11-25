(ns top.kzre.krro.plugin.painting.core.layer.undo.new-raster-layer
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.plugin.painting.core.layer.tiles :as tiles]
    [top.kzre.krro.plugin.painting.core.ops.snapshot :as snap]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.project.raster-layer :as pr]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.plugin.undo.core :as undo])
  (:import
    (top.kzre.krro.util.tile TiledCanvas)))

(defn- undo-key [] ::new-raster-layer)

(defn record-new-raster-layer!
  [canvas-id layer-path
   {:keys [id
           ^TiledCanvas canvas]
    :as layer}]
  (let [tile-size (.getTileSize canvas)
        wrapped   (snap/wrap-tiled-canvas canvas)
        layers (-> (record/record canvas-id)
                   (record/layers))
        dirty-tiles (tiles/global-tiles layers layer-path tile-size)]
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
       (pr/delete-raster! layer-id)
       (store/dispatch
         [:refresh-canvas canvas-id dirty-tiles])))
   :redo
   (fn [{:keys [new-metadata]}
        cb]
     (log/info "redo interceptor")
     (let [{:keys [canvas-id layer-id dirty-tiles snapshot]} new-metadata]
       (let [canvas (snap/read-tiled-canvas snapshot)]
         (pr/create-raster* layer-id canvas-id canvas))
       (cb)
       (store/dispatch
         [:refresh-canvas canvas-id dirty-tiles])))})
