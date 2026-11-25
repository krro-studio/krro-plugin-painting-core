(ns top.kzre.krro.plugin.painting.core.layer.tiles
  (:require [top.kzre.krro.canvas.core.layer.group :as group]
            [top.kzre.krro.canvas.core.layer.path :as layer.path]
            [top.kzre.krro.plugin.painting.core.project.vector-layer :as pv]
            [top.kzre.krro.canvas.vector.core :as cv]
            [top.kzre.krro.canvas.core.layer.util :as lu])
  (:import (top.kzre.krro.canvas.core.layer LayerUtils)
           (top.kzre.krro.util.tile TiledCanvas)))

(defmulti layer-tiles
          "获取图层占据的本地分块坐标"
          (fn [layer _tile-size] (:type layer)))

;; 默认返回nil，表示无法计算.
(defmethod layer-tiles :default [_ _] nil)

;; 默认返回nil，表示无法计算.
(defmethod layer-tiles :raster [layer _]
  (when-let [^TiledCanvas canvas (:canvas layer)]
    (set (.getTiles canvas))))

(defmethod layer-tiles :vector [layer _]
  (reduce
    (fn [acc p]
      (into acc (pv/path-tiles p)))
    #{}
    (vals (cv/paths layer))))


(defn global-tiles
  [layers layer-path tile-size]
  (let [dirty-tiles-atom (atom #{})
        uncomputable? (atom false)]
    (layer.path/run-path!
      layers layer-path
      (fn [_ layer]
        (when-not @uncomputable?
          (when-not (group/group? layer)
            (if-let [tiles (layer-tiles layer tile-size)]
              (let [layer-transform (lu/layer-transform layer-path layers)
                    dirty-tiles (set (LayerUtils/transformTiles tiles tile-size layer-transform))]
                (swap! dirty-tiles-atom into dirty-tiles))
              (reset! uncomputable? true))))))
    (if @uncomputable?
      nil
      @dirty-tiles-atom)))