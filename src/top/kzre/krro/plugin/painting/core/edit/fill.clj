(ns top.kzre.krro.plugin.painting.core.edit.fill
  "填充工具（re-frame 事件驱动）"
  (:require
    [top.kzre.krro.canvas.core.layer.util :as util]
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.brush.core :as brush]
    [top.kzre.krro.plugin.painting.core.edit.tool :refer [cleanup-tool-interceptor]]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.plugin.painting.core.tool.util :as tool-util]
    [top.kzre.krro.plugin.painting.core.viewport :as vp]))

(rf/reg-event-fx
  store/app-id :fill-tool/release
  [(cleanup-tool-interceptor)]
  (fn [cofx [_ record-id event-map frame]]
    (let [record (:record cofx)
          layer-id (get-in record [:canvas-data :current-layer-id])
          layers (get-in record [:canvas-data :layers])
          layer (util/find-layer layer-id layers)]
      (if (= :raster (:type layer))
        (let [layer-transform-inv (tool-util/layer-transform-inverse layer layers)
              logic-pos (vp/screen->logic (vp/get-viewport frame)
                                          (:x event-map) (:y event-map))
              local-pos (util/transform-point layer-transform-inv
                                              (:x logic-pos) (:y logic-pos))
              brush-spec (brush/get-global-brush)]
          {:dispatch [:oplog/fill record-id layer-id
                      {:brush-spec brush-spec
                       :x (:x local-pos)
                       :y (:y local-pos)}
                      ;; 上下文参数
                      :undo? true]})
        {:fx [[:warn "Fill tool is only work for raster layer!"]]}))))