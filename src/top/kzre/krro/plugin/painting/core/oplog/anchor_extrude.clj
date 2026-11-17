(ns top.kzre.krro.plugin.painting.core.oplog.anchor-extrude
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.canvas.vector.core :as cv]
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.changes.path :as change]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.store :as store]))

(rf/reg-event-fx
  store/app-id :oplog/anchor-extrude
  (fn [cofx [_ canvas-id layer-id end-anchor
             & {:keys [point]
                :as ctx}]]
    (let [{:keys [layer layer-transform]} (record/layer-context (:record cofx) layer-id)
          old-paths (cv/paths layer)
          new-point (or point (cv/anchor-point old-paths end-anchor))
          result    (cv/extrude-anchor old-paths end-anchor new-point)]
      (if result
        (let [{:keys [paths]} result
              path-id  (:path-id end-anchor)
              new-path (get paths path-id)
              op       (change/make-vector-paths-geometry-changed
                         layer-id old-paths layer-transform
                         :saved {path-id new-path})]
          {:dispatch [:oplog/log canvas-id op ctx]})
        (do
          (log/info "anchor-extrude: end-anchor is not an endpoint"
                    {:canvas-id  canvas-id
                     :layer-id   layer-id
                     :end-anchor end-anchor})
          nil)))))