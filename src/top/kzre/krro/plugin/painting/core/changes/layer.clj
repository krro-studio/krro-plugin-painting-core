(ns top.kzre.krro.plugin.painting.core.changes.layer
  "图层整体操作"
  (:require
   [top.kzre.krro.canvas.core.layer.path :as layer.path]
   [top.kzre.krro.core.util.diff :as diff]
   [top.kzre.krro.plugin.painting.core.changes.composite :as composite]
   [top.kzre.krro.plugin.painting.core.oplog.protocol :as proto]
   [top.kzre.krro.plugin.undo.core :as undo]
   [top.kzre.krro.plugin.painting.core.layer.tiles :as tiles]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]))

(defrecord LayerVisibilityChanged
  [layer-id visible? dirty-tiles dirty-transform]
  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] (empty? dirty-tiles))
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record _ctx]
    (let [canvas-id (:canvas-id record)
          layers (-> record :canvas-data :layers)
          path (layer.path/get-path layers layer-id)
          layers' (layer.path/update-layer
                    layers
                    path
                    (fn [l]
                      (assoc l :visible? visible?)))]
      [(assoc-in record [:canvas-data :layers]
                 layers')
       [[:render-canvas canvas-id this]
        [:rerender-canvas-frame canvas-id]]]))

  (record! [_ _record]
    (undo/record-state!)))

(defn make-layer-visible-changed
  [layer visible? dirty-transform]
  (let [dirty-tiles (tiles/layer-tiles layer pc/global-tile-size)]
    (->LayerVisibilityChanged
      (:id layer) visible?
      dirty-tiles dirty-transform)))