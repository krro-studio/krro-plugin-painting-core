(ns top.kzre.krro.plugin.painting.core.layer.events
  "事件注册（业务逻辑）"
  (:require
    [top.kzre.krro.canvas.core.layer.util :as lu]
    [top.kzre.krro.canvas.perspective.core :as perspective]
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.state :as state]
    [top.kzre.krro.plugin.painting.core.store :as store]))


(rf/reg-event-fx
  store/app-id :new-raster-layer
  (fn [cofx [_ record-id]]
    (let [record (:record cofx)
          current-layer-id (record/current-layer-id record)]
      {:dispatch [:oplog/new-raster-layer
                  record-id current-layer-id
                  {:undo? true}]})))

(rf/reg-event-fx
  store/app-id :new-vector-layer
  (fn [cofx [_ record-id]]
    (let [record (:record cofx)
          current-layer-id (record/current-layer-id record)]
      {:dispatch [:oplog/new-vector-layer
                  record-id current-layer-id
                  {:undo? true}]})))

(rf/reg-event-fx
  store/app-id :new-layer-group
  (fn [cofx [_ record-id]]
    (let [record (:record cofx)]
      {:dispatch [:oplog/new-layer-group
                  record-id
                  {:selected-layer-ids (record/selected-layer-ids record)
                   :selected-layer-id (record/selected-layer-id record)}
                  {:undo? true}]})))

(rf/reg-event-fx
  store/app-id :delete-selected-layers
  (fn [cofx [_ record-id]]
    (let [record (:record cofx)]
      {:dispatch [:oplog/delete-selected-layers
                  record-id
                  {:selected-layer-ids (record/selected-layer-ids record)
                   :selected-layer-id (record/selected-layer-id record)}
                  {:undo? true}]})))


(rf/reg-event-fx
  store/app-id :new-perspective-layer
  (fn [cofx [_ record-id]]
    (let [record (:record cofx)
          canvas-data (:canvas-data record)
          selected-id (get-in record [:canvas-data :current-layer-id])
          layers (get canvas-data :layers)
          path (lu/above-layer-path selected-id layers)
          new-layer (perspective/make-perspective-layer)
          layer-id (:id new-layer)
          new-record (-> record
                         (record/insert-layer-at-path path new-layer)
                         (record/set-current-layer layer-id)
                         (record/select-layer layer-id))]
      {:record new-record
       :fx [[:record-perspective-layer-added record-id layer-id]  ;; undo 记录
            [:rerender-canvas-frame record-id]]})))        ;; UI 刷新




(rf/reg-event-fx
  store/app-id :move-layer
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))

(rf/reg-event-fx
  store/app-id :duplicate-raster-layer
  (fn [cofx [_ record-id from-path to-path]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))

(rf/reg-event-fx
  store/app-id :duplicate-vector-layer
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))


(rf/reg-event-fx
  store/app-id :duplicate-perspective-layer
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))