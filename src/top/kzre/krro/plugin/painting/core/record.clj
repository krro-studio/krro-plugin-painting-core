(ns top.kzre.krro.plugin.painting.core.record
  (:require
   [clojure.spec.alpha :as s]
   [top.kzre.krro.canvas.core.layer.core :as layer.core]
   [top.kzre.krro.canvas.core.layer.path :as path]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.core.store :as store]
   [top.kzre.krro.plugin.painting.core.tool.util :as tool-util])
  (:import
   (top.kzre.krro.util.math KMath)))

;; TODO 非画布记录，1. 合并全局数据 2. 做别的 record
(s/def ::canvas-id keyword?)

;; =============================== 项目数据 =====================================
(s/def ::canvas-data ::pc/canvas-data)

(s/def ::x number?)
(s/def ::y number?)
(s/def ::cursor-position (s/keys
                           :req-un [::x ::y]))


(s/def ::canvas-record
  (s/keys :req-un [::canvas-id
                   ::canvas-data
                   ::canvas-state
                   ]
          :opt-un [::cursor-position
]))


(def record store/record)

(defn cursor-position [record]
  (get record :cursor-position))

(defn current-tool [record]
  (get record :current-tool))

(defn assoc-current-tool [record tool-id]
  (assoc record :current-tool tool-id))


(defn current-layer-id
  [record]
  (get-in record [:canvas-data :current-layer-id]))

(defn select-layer
  [record layer-id]
  (-> record
      (assoc :selected-layer-id layer-id)
      (assoc :selected-layer-ids #{layer-id})))

(defn selected-layer-ids
  [record]
  (get record :selected-layer-ids #{}))

(defn selected-layer-id
  [record]
  (get record :selected-layer-id))

(defn select-layer-multi
  "向 record 的选中图层列表追加一个图层 ID（去重），并更新当前选中 ID 为该图层。"
  [record layer-id]
  (if layer-id
    (-> record
        (update :selected-layer-ids
                (fn [ids]
                  (conj (or ids #{}) layer-id)))
        (assoc :selected-layer-id layer-id))
    record))

(defn pivot-center [record]
  (get-in record [:canvas-data :pivot-center] :aabb-center))

(defn snap-options [record]
  (get-in record [:canvas-data :snap-options] {}))

(defn falloff-options [record]
  (get-in record [:canvas-data :falloff-options] {}))

(defn horizontal-mirror-enabled [record]
  (get-in record [:canvas-data :horizontal-mirror-enabled] false))

(defn horizontal-mirror-center [record]
  (get-in record [:canvas-data :horizontal-mirror-center]))

(defn vertical-mirror-enabled [record]
  (get-in record [:canvas-data :vertical-mirror-enabled] false))

(defn vertical-mirror-center [record]
  (get-in record [:canvas-data :vertical-mirror-center]))

(defn tiling-enabled [record]
  (get-in record [:canvas-data :tiling-enabled] false))

(defn insert-layer-at-path
  [record path layer]
  (update record :canvas-data pc/insert-layer-at-path path layer))

(defn set-current-layer
  [record layer-id]
  (assoc-in record [:canvas-data :current-layer-id] layer-id))

(defn layers [record]
  (get-in record [:canvas-data :layers]))


(defn layer-context [record & [target-layer-id]]
  (let [;; 基础数据
        canvas-data (:canvas-data record)
        layer-id (or target-layer-id (:current-layer-id canvas-data))
        layers (layers record)

        ;; 图层信息
        layer-path (when layer-id (path/get-path layers layer-id ))
        layer (when layer-path (path/get-layer layers layer-path))
        layer-type (when layer (layer.core/layer-type layer))
        layer-visible (when layer (layer.core/visible? layer))
        layer-transform-inv (when layer (tool-util/layer-transform-inverse layer layers))
        layer-transform (when layer-transform-inv (KMath/mat2dInv layer-transform-inv))]
    {:layer-id            layer-id
     :layer-path          layer-path
     :layer-type          layer-type
     :layer-visible       layer-visible
     :layer               layer
     :layer-transform     layer-transform
     :layer-transform-inv layer-transform-inv
     :layers              layers
     :canvas-id         (:id canvas-data)
     :canvas-data         canvas-data}))

