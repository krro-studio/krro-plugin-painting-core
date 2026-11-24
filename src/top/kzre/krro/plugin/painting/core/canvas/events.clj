(ns top.kzre.krro.plugin.painting.core.canvas.events
  "画布相关的事件处理器"
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.changes.viewport :as viewport]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.store :as store]))

;; 单选图层
(rf/reg-event-fx
  store/app-id :select-layer
  (fn [cofx [_ record-id layer-id]]
    (let [record (:record cofx)]
      {:record (-> record
                   (record/set-current-layer layer-id)
                   (record/select-layer layer-id))
       :fx [[:rerender-canvas-frame record-id]]})))

;; 多选图层
(rf/reg-event-fx
  store/app-id :select-layer-multi
  (fn [cofx [_ record-id layer-id]]
    (let [new-record (record/select-layer-multi (:record cofx) layer-id)]
      {:record new-record
       :fx [[:rerender-canvas-frame record-id]]}
      )))


;; 日志
(rf/reg-event-fx
  store/app-id :log-layers
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))



;; 刷新和画布相关的所有UI和画布
(rf/reg-event-fx
  store/app-id :refresh-canvas
  (fn [_ [_ record-id]]
    {:fx [[:render-canvas record-id (viewport/refreshed)]
          [:rerender-canvas-frame record-id]]}))

(rf/reg-event-fx
  store/app-id :canvas/set-cursor-position
  (fn [cofx [_ _ cursor-position]]
    {:record (-> (:record cofx)
                 (assoc :cursor-position cursor-position))}))

(rf/reg-event-fx
  store/app-id :close-canvas
  (fn [cofx [_ record-id]]
    {:fx [:close-canvas-render-channel record-id]}))