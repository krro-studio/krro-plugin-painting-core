(ns top.kzre.krro.plugin.painting.core.oplog.fill
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.changes.raster-layer :as change]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.store :as store])
  (:import
    (top.kzre.colorutils.color RGB)
    (top.kzre.krro.canvas.raster.bloodfill
      ColorMatchers FloodFillExecutor FloodFillRequest LineArtDetectors)
    (top.kzre.krro.util.tile TiledCanvas)))

(defn- build-fill-request
  [^TiledCanvas canvas brush-spec local-x local-y width height]
  (let [fill-color       (get brush-spec :color (RGB/rgba 0.0 0.0 0.0 1.0))
        tolerance        (float (get brush-spec :fill-tolerance 0.0))
        contiguous       (get brush-spec :fill-contiguous true)
        anti-alias       (get brush-spec :fill-anti-alias true)
        blend-mode       (get brush-spec :fill-blend-mode nil)
        protect-opacity  (get brush-spec :fill-protect-opacity false)
        expand-radius    (float (get brush-spec :fill-expand-radius 0.0))
        protect-line-art (get brush-spec :fill-protect-line-art false)]
    (-> (FloodFillRequest/newBuilder)
        (.targetCanvas canvas)
        (.seed (double local-x) (double local-y))
        (.fillColor (float-array fill-color))
        (.tolerance tolerance)
        (.contiguous contiguous)
        (.antiAlias anti-alias)
        (.blendMode blend-mode)
        (.protectOpacity protect-opacity)
        (.expandRadius expand-radius)
        (.protectLineArt protect-line-art)
        (.colorMatcher (ColorMatchers/euclideanSquare))
        (.lineArtDetector (LineArtDetectors/defaultDetector))
        (.mask nil)
        (.size width height)
        (.build))))

(rf/reg-event-fx
  store/app-id :oplog/fill
  (fn [cofx [_ record-id layer-id {:keys [brush-spec x y]}
             & {:keys [] :as ctx}]]
    (let [record (:record cofx)
          {:keys [layer layer-transform]}
          (record/layer-context record layer-id)
          ;; base：操作前状态，归 op 所有。
          ;; - 撤销/重做：调用方通过 :canvas 传入并转移所有权，直接用
          ;; - 正常流程：copy 图层画布，绝不引用 (:canvas layer) 本体
          old-canvas (.copy ^TiledCanvas (:canvas layer))
          ;; 填充是原地修改，目标必须是独立的一份（op 拥有的"操作后"）
          filled-canvas (.copy ^TiledCanvas old-canvas)
          width  (get-in record [:canvas-data :width])
          height (get-in record [:canvas-data :height])
          request (build-fill-request filled-canvas brush-spec x y width height)
          result  (FloodFillExecutor/fill request)
          dirties (.getDirtyTiles result)
          op (change/make-raster-layer-dirty
               layer-id
               old-canvas filled-canvas
               dirties layer-transform)]
      {:dispatch [:oplog/log record-id op ctx]})))