(ns top.kzre.krro.plugin.painting.core.schedule.raster-layer
  (:require
    [top.kzre.krro.core.util.computing-graph :as cg]
    [top.kzre.krro.core.util.promise :as promise]
    [top.kzre.krro.plugin.painting.core.schedule.graph :as graph]
    [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
    [top.kzre.krro.canvas.core.layer.core :as layer.core]))


;; 将光栅图层适配为原始输入节点
(defrecord RasterLayer [layer]
  proto/ILayer
  (layer-id [_] (layer.core/layer-id layer))
  (opacity [_] (layer.core/opacity layer))
  (canvas [_] (:canvas layer))
  (visible? [_] (layer.core/visible? layer))
  (transform [_] (:transform layer))
  (blend-mode [_] (layer.core/blend-mode layer)))

;; 将光栅图层适配为渲染节点
(defrecord RasterLayerNode [^RasterLayer layer]
  ;; 计算节点协议
  cg/INode
  (node-id [_] (proto/layer-id layer))
  (dependencies [_] #{})
  (compute [_ _]
    (promise/resolved layer))
  (release [_ result]
    ;(when (instance? AutoCloseable result)
    ;  (.close ^AutoCloseable result))
    ))

(defn make-raster-layer-node [raster-layer]
  (->RasterLayerNode (->RasterLayer raster-layer)))


(defmethod graph/build-leaf :raster
  [atom _]
  (let [node (make-raster-layer-node atom)]
    {:nodes [node]
     :root  node}))