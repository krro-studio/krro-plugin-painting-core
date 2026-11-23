(ns top.kzre.krro.plugin.painting.core.layer.tiles
  (:import (top.kzre.krro.util.tile TiledCanvas)))

(defmulti layer-tiles
          "获取图层占据的本地分块坐标"
          (fn [layer _tile-size] (:type layer)))

;; 默认返回nil，表示无法计算.
(defmethod layer-tiles :default [_ _] nil)

;; 默认返回nil，表示无法计算.
(defmethod layer-tiles :raster [layer _]
  (when-let [^TiledCanvas canvas (:canvas layer)]
    (set (.getTiles canvas))))

