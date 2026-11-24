(ns top.kzre.krro.plugin.painting.core.changes.layer
  "图层整体操作"
  (:require
    [top.kzre.krro.canvas.core.layer.core :as layer.core]
    [top.kzre.krro.canvas.core.layer.path :as layer.path]
    [top.kzre.krro.core.util.diff :as diff]
    [top.kzre.krro.plugin.painting.core.canvas.undo.edit-canvas :as undo.edit-canvas]
    [top.kzre.krro.plugin.painting.core.changes.composite :as composite]
    [top.kzre.krro.plugin.painting.core.layer.tiles :as tiles]
    [top.kzre.krro.plugin.painting.core.layer.undo.new-raster-layer :as undo-new-raster-layer]
    [top.kzre.krro.plugin.painting.core.oplog.protocol :as proto]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.undo.core :as undo]))

(defrecord LayerMetaDataChanged
  [layer-id metadata dirty-tiles dirty-transform]
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
                      ;; metadata 覆盖原属性
                      (merge l metadata)))]
      [(assoc-in record [:canvas-data :layers]
                 layers')
       [[:render-canvas canvas-id this]
        [:rerender-canvas-frame canvas-id]]]))

  (record! [_ _record]
    (undo/record-state!)))


(defn layer-metadata-changed-refresh
  "图层元信息改变，这会影响到渲染"
  [layer metadata dirty-transform]
  (let [dirty-tiles (tiles/layer-tiles layer pc/global-tile-size)]
    (->LayerMetaDataChanged
      (:id layer) metadata
      dirty-tiles dirty-transform)))

(defn layer-metadata-changed
  "图层元信息改变，但不影响渲染"
  [layer metadata dirty-transform]
  (->LayerMetaDataChanged
    (:id layer) metadata
    #{} dirty-transform))


(defrecord NewRasterLayer
  [target-path new-layer]
  proto/IOperation
  (realize [_ {:keys [canvas-id] :as record} _ctx]
    (let [layer-id (layer.core/layer-id new-layer)]
      [(-> record
           (record/insert-layer-at-path target-path new-layer)
           (record/set-current-layer layer-id)
           (record/select-layer layer-id))
       [[:save-raster-data canvas-id layer-id]              ;; 同步光栅数据到侧表
        [:rerender-canvas-frame canvas-id]]]))

  (record! [_ {:keys [canvas-id] :as _record}]
    (undo-new-raster-layer/record-new-raster-layer! canvas-id target-path new-layer)))

(defn new-raster-layer [path layer]
  (->NewRasterLayer path layer))

(defrecord NewPersistentLayer
  [target-path new-layer]
  proto/IOperation
  (realize [_ {:keys [canvas-id] :as record} _ctx]
    (let [layer-id (layer.core/layer-id new-layer)]
      [(-> record
           (record/insert-layer-at-path target-path new-layer)
           (record/set-current-layer layer-id)
           (record/select-layer layer-id))
       [[:rerender-canvas-frame canvas-id]]]))

  (record! [_ {:keys [canvas-id] :as _record}]
    (undo.edit-canvas/record-edit-canvas! canvas-id)))

(defn new-persistent-layer
  [path layer]
  (->NewPersistentLayer path layer))