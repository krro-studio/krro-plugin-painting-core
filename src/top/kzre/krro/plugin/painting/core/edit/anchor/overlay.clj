(ns top.kzre.krro.plugin.painting.core.edit.anchor.overlay
  (:require [top.kzre.krro.plugin.painting.core.edit.common :as common]
            [top.kzre.krro.canvas.vector.anchor :as anchor]
            [top.kzre.krro.canvas.vector.bezier.handle :as bezier.handle]))

(defn make-context
  [{:keys [active-anchor
           selected-anchors
           hover-anchor
           selected-paths] :as _state}
   {:keys [viewport
           layer
           layer-visible
           layer-transform
           transaction-kind] :as _tool-ctx}]
  (let [path-order (:path-order layer)
        paths (:paths layer)]
    {:path-order path-order
     :paths paths
     :layer-visible layer-visible
     :layer-transform layer-transform
     :viewport viewport
     :active-anchor active-anchor
     :selected-anchors selected-anchors
     :hover-anchor hover-anchor
     :selected-paths selected-paths
     :transaction-kind transaction-kind
     }))

(defn get-path [ctx path-id]
  (get (:paths ctx) path-id))

(defn layer-point->screen
  "图层上一点变换到屏幕"
  [ctx point]
  (let [{:keys [viewport layer-transform]} ctx]
    (common/layer-point->screen
      point layer-transform viewport)))

(defn reduce-paths
  "路径遍历辅助"
  [f init
   {:keys [path-order] :as ctx} ]
  (reduce
    (fn [acc path-id]
      (f acc path-id ctx))
    init
    path-order))

(defmulti describe-path
          (fn [tx-kind _path-id _ctx]
            tx-kind))

(defn describe-overlay
  "产出所有 路径 的描述"
  [{:keys [transaction-kind
           init]
    :as ctx}]
  (reduce-paths
    (fn [acc path-id _ctx]
      (into acc
            (describe-path transaction-kind path-id ctx)))
    (or init [])
    ctx))

(defn- anchor-state
  "锚点选择状态分类."
  [anchor {:keys [active-anchor selected-anchors hover-anchor]}]
  (cond
    (= anchor active-anchor) :active
    (contains? selected-anchors anchor) :selected
    (= anchor hover-anchor) :hover
    :else :normal))

(defn- describe-bezier-anchor
  [anchor {:keys [paths] :as ctx}]
  (let [st (anchor-state anchor ctx)
        point (anchor/anchor-point paths anchor)
        screen-point  (layer-point->screen ctx point)]
    [[:anchor {:x (:x screen-point) :y (:y screen-point) :state st :shape :square}]]))

;; TODO bezier segment 的轴线显示
;; TODO handle 选择的显示逻辑
(defn- describe-bezier-handle
  "贝塞尔控制手柄：锚点 → 入/出控制点的引线 + 小圆。
   使用 overlay 的 :handle 描述项，端点样式 (:in/:out 差异) 由下层处理。"
  [handle {:keys [paths] :as ctx}]
  (let [{:keys [anchor direction]} handle
        ap (layer-point->screen ctx (anchor/anchor-point paths anchor))
        point (layer-point->screen ctx (bezier.handle/handle-point paths handle))]
    [[:handle {:x (:x ap) :y (:y ap)
               :hx (:x point) :hy (:y point)
               :direction direction}]]))

(defn describe-bezier
  "默认的bezier曲线描述逻辑, 不考虑事务"
  [path-id {:keys [paths] :as ctx}]
  (let [anchors (anchor/all-anchors paths path-id)]
    (mapcat
      (fn [a]
        (concat (describe-bezier-anchor a ctx)
                (describe-bezier-handle
                  (bezier.handle/->Handle a :in) ctx)
                (describe-bezier-handle
                  (bezier.handle/->Handle a :out) ctx)))
      anchors)))



;; 默认的路径描述
(defmethod describe-path :default [_ path-id ctx]
  (if (= (:path-type (get-path ctx path-id)) :bezier)
   (describe-bezier path-id ctx)
   []))