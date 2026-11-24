(ns top.kzre.krro.plugin.painting.core.canvas.subs
  "画布相关的反应式订阅定义"
  (:require [top.kzre.krro.core.reframe.core :as rf]
            [top.kzre.krro.plugin.painting.core.store :as store]
            [top.kzre.krro.plugin.painting.core.record :as record]))

(rf/reg-sub
  store/app-id
  :canvas-data
  :<- [:record]
  :canvas-data)                              ; keyword 作为提取函数


(rf/reg-sub
  store/app-id
  :layers
  :<- [:canvas-data]                         ; 依赖上层订阅
  :layers)

;; 图像尺寸
(rf/reg-sub
  store/app-id
  :image-size
  :<- [:canvas-data]
  (fn [canvas-data] (select-keys canvas-data [:width :height])))

;; 当前图层
(rf/reg-sub
  store/app-id
  :current-layer-id
  record/current-layer-id)

;;被选中的图层
(rf/reg-sub
  store/app-id
  :selected-layer-ids
  record/selected-layer-ids)


;; 当前工具
(rf/reg-sub
  store/app-id
  :current-tool
  record/current-tool)

;; 当前光标位置
(rf/reg-sub
  store/app-id
  :canvas/cursor-position
  (fn [record]
    (get-in record [:record :cursor-position])))