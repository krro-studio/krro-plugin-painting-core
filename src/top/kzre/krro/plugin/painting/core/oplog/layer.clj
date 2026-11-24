(ns top.kzre.krro.plugin.painting.core.oplog.layer
  (:require
   [top.kzre.krro.canvas.core.layer.core :as layer.core]
   [top.kzre.krro.canvas.core.layer.path :as layer.path]
   [top.kzre.krro.canvas.raster.core :as raster]
   [top.kzre.krro.canvas.vector.core :as vector]
   [top.kzre.krro.core.reframe.core :as rf]
   [top.kzre.krro.plugin.painting.core.changes.layer :as changes.layer]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.core.project.layer :as project.layer]
   [top.kzre.krro.plugin.painting.core.record :as record]
   [top.kzre.krro.plugin.painting.core.store :as store]))

(defn- new-layer-path [record layer-id]
  (let [layers (record/layers record)]
    (if-let [current-path
             (when layer-id
               (layer.path/get-path layers layer-id))]
      (layer.path/unchecked-above-sibling-path current-path)
      (layer.path/above-top-path layers))))

(rf/reg-event-fx
  store/app-id :oplog/new-raster-layer
  (fn [cofx [_ record-id layer-id ctx]]
    (let [record (:record cofx)
          target-path (new-layer-path record layer-id)
          new-layer (raster/make-raster-layer pc/global-tile-size)
          op (changes.layer/new-raster-layer target-path new-layer)]
      {:dispatch [:oplog/log record-id op ctx]})))

(rf/reg-event-fx
  store/app-id :oplog/new-vector-layer
  (fn [cofx [_ record-id layer-id ctx]]
    (let [record (:record cofx)
          target-path (new-layer-path record layer-id)
          new-layer (vector/make-vector-layer :antialias false)
          op (changes.layer/new-persistent-layer target-path new-layer)]
      {:dispatch [:oplog/log record-id op ctx]})))


(rf/reg-event-fx
  store/app-id :oplog/toggle-layer-visibility
  (fn [cofx [_ record-id layer-id ctx]]
    (let [{:keys [layer layer-transform]}
          (record/layer-context (:record cofx) layer-id)
          op (changes.layer/layer-metadata-changed-refresh
               layer
               {:visible? (not (boolean (layer.core/visible? layer)))}
               layer-transform)]
      {:dispatch [:oplog/log record-id op ctx]})))


(rf/reg-event-fx
  store/app-id :oplog/toggle-layer-lock
  (fn [cofx [_ record-id layer-id ctx]]
    (let [{:keys [layer layer-transform]}
          (record/layer-context (:record cofx) layer-id)
          op (changes.layer/layer-metadata-changed-refresh
               layer
               {:locked? (not (boolean (project.layer/locked? layer)))}
               layer-transform)]
      {:dispatch [:oplog/log record-id op ctx]})))

(rf/reg-event-fx
  store/app-id :oplog/toggle-layer-alpha-lock
  (fn [cofx [_ record-id layer-id ctx]]
    (let [{:keys [layer layer-transform]}
          (record/layer-context (:record cofx) layer-id)
          op (changes.layer/layer-metadata-changed
               layer
               {:alpha-locked? (not (boolean (project.layer/alpha-locked? layer)))}
               layer-transform)]
      {:dispatch [:oplog/log record-id op ctx]})))

(rf/reg-event-fx
  store/app-id :oplog/set-layer-name
  (fn [cofx [_ record-id layer-id layer-name ctx]]
    (let [{:keys [layer layer-transform]}
          (record/layer-context (:record cofx) layer-id)
          op (changes.layer/layer-metadata-changed
               layer
               {:name layer-name}
               layer-transform)]
      {:dispatch [:oplog/log record-id op ctx]})))