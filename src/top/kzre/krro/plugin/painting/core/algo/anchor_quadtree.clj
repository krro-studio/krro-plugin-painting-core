(ns top.kzre.krro.plugin.painting.core.algo.anchor-quadtree
  (:require
    [top.kzre.krro.canvas.vector.core :as cv]
    [top.kzre.krro.canvas.vector.bezier.handle :as bezier-handle]
    [top.kzre.krro.canvas.vector.path :as vector.path])
  (:import (top.kzre.curve.bezier2d AABB)
           (top.kzre.krro.canvas.core QuadTree Rect RectQuadTree)))

;; ========== 内部工具 ==========

(defn- aabb->rect
  ^Rect [^AABB aabb]
  (Rect. (double (.getMinX aabb))
         (double (.getMinY aabb))
         (double (.getMaxX aabb))
         (double (.getMaxY aabb))))

(defn- get-point-safe
  [paths path-id point-idx]
  (let [path (get paths path-id)]
    (when path
      (let [points (get-in path [:curve :points])]
        (when (and points (< point-idx (count points)))
          (nth points point-idx))))))

(defn- get-handle-point-safe
  "通过 handle 对象获取点坐标 {:x :y}，拿不到返回 nil。"
  [paths handle]
  (when handle
    (bezier-handle/handle-point paths handle)))

(defn- anchor-handles
  "返回锚点对应的 [in-handle out-handle]，拿不到对应位置为 nil。"
  [anchor]
  (bezier-handle/anchor-handles anchor))

(defn- seg-rects-for-path
  [paths path-id]
  (let [path (get paths path-id)]
    (when path
      (mapv aabb->rect (cv/seg-aabbs* path)))))

(defn- anchor-affected-seg-idxs
  [paths anchor]
  (cv/anchor-seg-idxs paths anchor))

(defn- seg-segment
  [path-id seg-idx]
  (vector.path/->Segment path-id seg-idx))

;; ========== 内部：handle 点四叉树维护 ==========

(defn- insert-anchor-handles!
  [^QuadTree tree paths anchor]
  (let [[in-handle out-handle] (anchor-handles anchor)]
    (when-let [pt (get-handle-point-safe paths in-handle)]
      (.insert tree (double (:x pt)) (double (:y pt)) in-handle))
    (when-let [pt (get-handle-point-safe paths out-handle)]
      (.insert tree (double (:x pt)) (double (:y pt)) out-handle)))
  tree)

(defn- delete-anchor-handles!
  [^QuadTree tree paths anchor]
  (let [[in-handle out-handle] (anchor-handles anchor)]
    (when-let [pt (get-handle-point-safe paths in-handle)]
      (.delete tree (double (:x pt)) (double (:y pt)) in-handle))
    (when-let [pt (get-handle-point-safe paths out-handle)]
      (.delete tree (double (:x pt)) (double (:y pt)) out-handle)))
  tree)

(defn- update-anchor-handles!
  [^QuadTree tree old-paths new-paths anchor]
  (let [[old-in old-out] (anchor-handles anchor)
        [new-in new-out] (anchor-handles anchor)]
    ;; 删旧
    (when-let [pt (get-handle-point-safe old-paths old-in)]
      (.delete tree (double (:x pt)) (double (:y pt)) old-in))
    (when-let [pt (get-handle-point-safe old-paths old-out)]
      (.delete tree (double (:x pt)) (double (:y pt)) old-out))
    ;; 插新
    (when-let [pt (get-handle-point-safe new-paths new-in)]
      (.insert tree (double (:x pt)) (double (:y pt)) new-in))
    (when-let [pt (get-handle-point-safe new-paths new-out)]
      (.insert tree (double (:x pt)) (double (:y pt)) new-out)))
  tree)

;; ========== 内部：段四叉树原语 ==========

(defn- seg-delete!
  [^RectQuadTree tree paths path-id seg-idx]
  (let [rects (seg-rects-for-path paths path-id)]
    (when rects
      (when (< seg-idx (count rects))
        (let [rect (nth rects seg-idx)
              seg  (seg-segment path-id seg-idx)]
          (.delete tree rect seg))))))

(defn- seg-insert!
  [^RectQuadTree tree paths path-id seg-idx]
  (let [rects (seg-rects-for-path paths path-id)]
    (when rects
      (when (< seg-idx (count rects))
        (let [rect (nth rects seg-idx)
              seg  (seg-segment path-id seg-idx)]
          (.insert tree rect seg))))))

(defn- seg-update!
  [^RectQuadTree tree old-paths new-paths path-id seg-idx]
  (seg-delete! tree old-paths path-id seg-idx)
  (seg-insert! tree new-paths path-id seg-idx))

(defn- seg-update-same!
  [^RectQuadTree tree paths path-id seg-idx]
  (seg-delete! tree paths path-id seg-idx)
  (seg-insert! tree paths path-id seg-idx))

(defn- path-segs-delete!
  [^RectQuadTree tree paths path-id]
  (let [path (get paths path-id)]
    (when path
      (let [aabbs (cv/seg-aabbs* path)
            cnt   (count aabbs)]
        (loop [i 0]
          (when (< i cnt)
            (let [rect (aabb->rect (nth aabbs i))
                  seg  (seg-segment path-id i)]
              (.delete tree rect seg))
            (recur (inc i)))))))
  tree)

(defn- path-segs-insert!
  [^RectQuadTree tree paths path-id]
  (let [path (get paths path-id)]
    (when path
      (let [aabbs (cv/seg-aabbs* path)
            cnt   (count aabbs)]
        (loop [i 0]
          (when (< i cnt)
            (let [rect (aabb->rect (nth aabbs i))
                  seg  (seg-segment path-id i)]
              (.insert tree rect seg))
            (recur (inc i)))))))
  tree)

;; ========== 点四叉树（锚点+handle）构建 ==========

(defn build-anchor-quadtree
  [paths]
  (let [tree (QuadTree.)]
    (doseq [[path-id path] paths]
      (let [points (get-in path [:curve :points])]
        (when (seq points)
          (let [cnt (count points)]
            (loop [idx 0]
              (when (< idx cnt)
                (let [pt     (nth points idx)
                      anchor (cv/->Anchor path-id idx)]
                  ;; 锚点本身
                  (.insert tree
                           (double (:x pt))
                           (double (:y pt))
                           anchor)
                  ;; handle 点（原样插入）
                  (let [[in-handle out-handle] (anchor-handles anchor)]
                    (when-let [in-pt (get-handle-point-safe paths in-handle)]
                      (.insert tree
                               (double (:x in-pt))
                               (double (:y in-pt))
                               in-handle))
                    (when-let [out-pt (get-handle-point-safe paths out-handle)]
                      (.insert tree
                               (double (:x out-pt))
                               (double (:y out-pt))
                               out-handle))))
                (recur (inc idx))))))))
    tree))

;; ========== 对外 API：锚点操作 ==========

(defn insert-anchors!
  [^QuadTree anchor-tree ^RectQuadTree seg-tree paths anchors]
  (let [cnt (count anchors)]
    (loop [i 0]
      (when (< i cnt)
        (let [anchor (nth anchors i)
              pt     (get-point-safe paths (:path-id anchor) (:point-idx anchor))]
          (when pt
            ;; 锚点本身
            (.insert anchor-tree (double (:x pt)) (double (:y pt)) anchor)
            ;; handle 点
            (insert-anchor-handles! anchor-tree paths anchor)
            ;; 相邻段 AABB 变了
            (let [[left right] (anchor-affected-seg-idxs paths anchor)]
              (when left
                (seg-update-same! seg-tree paths (:path-id anchor) left))
              (when right
                (seg-update-same! seg-tree paths (:path-id anchor) right)))))
        (recur (inc i)))))
  anchor-tree)

(defn delete-anchors!
  [^QuadTree anchor-tree ^RectQuadTree seg-tree paths anchors]
  (let [cnt (count anchors)]
    (loop [i 0]
      (when (< i cnt)
        (let [anchor (nth anchors i)
              pt     (get-point-safe paths (:path-id anchor) (:point-idx anchor))]
          (when pt
            ;; 锚点本身
            (.delete anchor-tree (double (:x pt)) (double (:y pt)) anchor)
            ;; handle 点
            (delete-anchor-handles! anchor-tree paths anchor)
            ;; 相邻段 AABB 变了
            (let [[left right] (anchor-affected-seg-idxs paths anchor)]
              (when left
                (seg-update-same! seg-tree paths (:path-id anchor) left))
              (when right
                (seg-update-same! seg-tree paths (:path-id anchor) right)))))
        (recur (inc i)))))
  anchor-tree)

(defn update-anchors!
  [^QuadTree anchor-tree ^RectQuadTree seg-tree old-paths new-paths anchors]
  (let [cnt (count anchors)]
    (loop [i 0]
      (when (< i cnt)
        (let [anchor    (nth anchors i)
              old-pt    (get-point-safe old-paths (:path-id anchor) (:point-idx anchor))
              new-pt    (get-point-safe new-paths (:path-id anchor) (:point-idx anchor))]
          ;; 锚点本身
          (when old-pt
            (.delete anchor-tree (double (:x old-pt)) (double (:y old-pt)) anchor))
          (when new-pt
            (.insert anchor-tree (double (:x new-pt)) (double (:y new-pt)) anchor))
          ;; handle 点
          (update-anchor-handles! anchor-tree old-paths new-paths anchor)
          ;; 旧位置相邻段
          (let [[old-left old-right] (anchor-affected-seg-idxs old-paths anchor)]
            (when old-left
              (seg-update! seg-tree old-paths new-paths (:path-id anchor) old-left))
            (when old-right
              (seg-update! seg-tree old-paths new-paths (:path-id anchor) old-right)))
          ;; 新位置相邻段
          (let [[new-left new-right] (anchor-affected-seg-idxs new-paths anchor)]
            (when new-left
              (seg-update! seg-tree old-paths new-paths (:path-id anchor) new-left))
            (when new-right
              (seg-update! seg-tree old-paths new-paths (:path-id anchor) new-right))))
        (recur (inc i)))))
  anchor-tree)

(defn insert-path-anchors!
  [^QuadTree anchor-tree ^RectQuadTree seg-tree paths path-id]
  (let [path (get paths path-id)]
    (when path
      (let [points (get-in path [:curve :points])
            cnt    (count points)]
        (loop [idx 0]
          (when (< idx cnt)
            (let [pt     (nth points idx)
                  anchor (cv/->Anchor path-id idx)]
              ;; 锚点本身
              (.insert anchor-tree
                       (double (:x pt))
                       (double (:y pt))
                       anchor)
              ;; handle 点
              (let [[in-handle out-handle] (anchor-handles anchor)]
                (when-let [in-pt (get-handle-point-safe paths in-handle)]
                  (.insert anchor-tree
                           (double (:x in-pt))
                           (double (:y in-pt))
                           in-handle))
                (when-let [out-pt (get-handle-point-safe paths out-handle)]
                  (.insert anchor-tree
                           (double (:x out-pt))
                           (double (:y out-pt))
                           out-handle))))
            (recur (inc idx)))))
      (path-segs-insert! seg-tree paths path-id)))
  anchor-tree)

(defn delete-path-anchors!
  [^QuadTree anchor-tree ^RectQuadTree seg-tree paths path-id]
  (let [path (get paths path-id)]
    (when path
      (let [points (get-in path [:curve :points])
            cnt    (count points)]
        (loop [idx 0]
          (when (< idx cnt)
            (let [pt     (nth points idx)
                  anchor (cv/->Anchor path-id idx)]
              ;; 锚点本身
              (.delete anchor-tree
                       (double (:x pt))
                       (double (:y pt))
                       anchor)
              ;; handle 点
              (delete-anchor-handles! anchor-tree paths anchor))
            (recur (inc idx)))))
      (path-segs-delete! seg-tree paths path-id)))
  anchor-tree)

(defn update-path-anchors!
  [^QuadTree anchor-tree ^RectQuadTree seg-tree old-paths new-paths path-id]
  (delete-path-anchors! anchor-tree seg-tree old-paths path-id)
  (insert-path-anchors! anchor-tree seg-tree new-paths path-id))

;; ========== 段四叉树构建 ==========

(defn build-segment-quadtree
  [paths]
  (let [tree (RectQuadTree.)]
    (doseq [[path-id path] paths]
      (let [aabbs (cv/seg-aabbs* path)]
        (when (seq aabbs)
          (let [cnt (count aabbs)]
            (loop [seg-idx 0]
              (when (< seg-idx cnt)
                (let [rect (aabb->rect (nth aabbs seg-idx))]
                  (.insert tree rect (seg-segment path-id seg-idx)))
                (recur (inc seg-idx))))))))
    tree))

;; ========== 对外 API：唯一段级批量入口 ==========

(defn update-segments!
  "直接按 seg-idx 批量操作段。用于曲线重采样、路径重构等场景。
   ops: [{:path-id pid :seg-idx idx :op :delete|:insert|:update} ...]"
  [^RectQuadTree tree old-paths new-paths ops]
  (let [cnt (count ops)]
    (loop [i 0]
      (when (< i cnt)
        (let [op       (nth ops i)
              path-id  (:path-id op)
              seg-idx  (:seg-idx op)
              op-type  (:op op)]
          (case op-type
            :delete (seg-delete! tree old-paths path-id seg-idx)
            :insert (seg-insert! tree new-paths path-id seg-idx)
            :update (seg-update! tree old-paths new-paths path-id seg-idx)
            nil))
        (recur (inc i)))))
  tree)