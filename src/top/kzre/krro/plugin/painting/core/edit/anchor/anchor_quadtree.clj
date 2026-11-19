(ns top.kzre.krro.plugin.painting.core.edit.anchor.anchor-quadtree
  (:require
    [top.kzre.krro.canvas.core.layer.util :as util]
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.algo.anchor-quadtree :as algo]
    [top.kzre.krro.plugin.painting.core.store :as store])
  (:import (top.kzre.krro.canvas.core QuadTree RectQuadTree)))

;; ========== 存储 ==========
;; 形态：{:canvas-id {:layer-id <layer-id>
;;                    :tree <QuadTree>
;;                    :rect-tree <RectQuadTree>}}

(defonce anchor-quadtrees (atom {}))

(defn tree
  "获取 canvas-id 对应的锚点四叉树。不存在返回 nil。"
  [canvas-id]
  (:tree (get @anchor-quadtrees canvas-id)))

(defn close
  [canvas-id]
  (swap! anchor-quadtrees dissoc canvas-id))

;; ========== 宏 ==========

(defmacro quadtree-fn
  "获取 canvas-id 对应的两棵四叉树，若存在则调用 fn-sym，
   并将 tree 和 rect-tree 作为前两个参数插入。"
  [canvas-id fn-sym & args]
  `(when-let [entry# (get @anchor-quadtrees ~canvas-id)]
     (when-let [~'tree (:tree entry#)]
       (when-let [~'rect-tree (:rect-tree entry#)]
         (~fn-sym ~'tree ~'rect-tree ~@args)))))

;; ========== 构建 ==========

(defn- build-for-layer
  [layer]
  (when (= :vector (:type layer))
    (let [paths (:paths layer)]
      [(algo/build-anchor-quadtree paths)
       (algo/build-segment-quadtree paths)])))

;; ========== Interceptor ==========

(defn anchor-quadtree-interceptor
  "确保 anchor-quadtree 存在且与当前图层一致。
   不一致（图层切换 / canvas 切换）则清空重建。"
  []
  {:before
   (fn [context]
     (when-let [canvas-id (get-in context [:coeffects :record-id])]
       (let [canvas-data (get-in context [:coeffects :record :canvas-data])]
         (when-let [current-layer-id (:current-layer-id canvas-data)]
           (let [layers (:layers canvas-data)
                 entry  (get @anchor-quadtrees canvas-id)]
             (if (not= (:layer-id entry) current-layer-id)
               ;; 图层切换 → 重建
               (when-let [layer (util/find-layer current-layer-id layers)]
                 (when-let [[tree rect-tree] (build-for-layer layer)]
                   (swap! anchor-quadtrees assoc canvas-id
                          {:layer-id  current-layer-id
                           :tree      tree
                           :rect-tree rect-tree})))
               ;; 图层一致但树为空
               (when (nil? (:tree entry))
                 (when-let [layer (util/find-layer current-layer-id layers)]
                   (when-let [[tree rect-tree] (build-for-layer layer)]
                     (swap! anchor-quadtrees assoc canvas-id
                            {:layer-id  current-layer-id
                             :tree      tree
                             :rect-tree rect-tree})))))))))
     context)})

;; ========== 对外 API：锚点操作（同时驱动段树） ==========

(defn update-anchors!
  [canvas-id old-paths new-paths anchors]
  (quadtree-fn canvas-id algo/update-anchors! old-paths new-paths anchors))

(defn delete-anchors!
  [canvas-id paths anchors]
  (quadtree-fn canvas-id algo/delete-anchors! paths anchors))

(defn insert-anchors!
  [canvas-id paths anchors]
  (quadtree-fn canvas-id algo/insert-anchors! paths anchors))

(defn delete-path!
  [canvas-id paths path-id]
  (quadtree-fn canvas-id algo/delete-path-anchors! paths path-id))

(defn insert-path!
  [canvas-id paths path-id]
  (quadtree-fn canvas-id algo/insert-path-anchors! paths path-id))

;; ========== 对外 API：段级批量操作（唯一直接段入口） ==========

(defn update-segments!
  [canvas-id old-paths new-paths ops]
  (quadtree-fn canvas-id algo/update-segments! old-paths new-paths ops))

;; ========== 重建 ==========

(defn rebuild!
  [canvas-id layer-id paths]
  (let [tree (algo/build-anchor-quadtree paths)
        rect-tree (algo/build-segment-quadtree paths)]
    (swap! anchor-quadtrees assoc canvas-id
           {:layer-id  layer-id
            :tree      tree
            :rect-tree rect-tree})))

;; ========== reframe fx 注册 ==========

(rf/reg-fx
  store/app-id :anchor-quadtree/update-anchors
  (fn [_ record-id old-paths new-paths anchors]
    (update-anchors! record-id old-paths new-paths anchors)))

(rf/reg-fx
  store/app-id :anchor-quadtree/delete-anchors
  (fn [_ record-id paths anchors]
    (delete-anchors! record-id paths anchors)))

(rf/reg-fx
  store/app-id :anchor-quadtree/insert-anchors
  (fn [_ record-id paths anchors]
    (insert-anchors! record-id paths anchors)))

(rf/reg-fx
  store/app-id :anchor-quadtree/insert-path
  (fn [_ record-id paths path-id]
    (insert-path! record-id paths path-id)))

(rf/reg-fx
  store/app-id :anchor-quadtree/delete-path
  (fn [_ record-id paths path-id]
    (delete-path! record-id paths path-id)))

(rf/reg-fx
  store/app-id :anchor-quadtree/update-segments
  (fn [_ record-id old-paths new-paths ops]
    (update-segments! record-id old-paths new-paths ops)))

(rf/reg-fx
  store/app-id :anchor-quadtree/rebuild
  (fn [_ record-id layer-id paths]
    (rebuild! record-id layer-id paths)))

(rf/reg-fx
  store/app-id :anchor-quadtree/close
  (fn [_ record-id]
    (close record-id)))