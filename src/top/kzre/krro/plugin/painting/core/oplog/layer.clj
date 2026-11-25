(ns top.kzre.krro.plugin.painting.core.oplog.layer
  (:require
   [top.kzre.krro.canvas.core.layer.core :as layer.core]
   [top.kzre.krro.canvas.core.layer.group :as group]
   [top.kzre.krro.canvas.core.layer.path :as layer.path]
   [top.kzre.krro.canvas.raster.core :as raster]
   [top.kzre.krro.canvas.vector.core :as vector]
   [top.kzre.krro.core.reframe.core :as rf]
   [top.kzre.krro.core.util.tree-path :as tp]
   [top.kzre.krro.plugin.painting.core.changes.layer :as changes.layer]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.core.project.layer :as project.layer]
   [top.kzre.krro.plugin.painting.core.layer.clone :as clone-layer]
   [top.kzre.krro.plugin.painting.core.record :as record]
   [top.kzre.krro.plugin.painting.core.store :as store]))

(defn- new-layer-path
  "新建图层时候要插入的路径"
  [record layer-id]
  (let [layers (record/layers record)]
    (if-let [current-path
             (when layer-id
               (layer.path/get-path layers layer-id))]
      (let [layer (layer.path/get-layer layers current-path)]
        (if (group/group? layer)
          (layer.path/above-last-child-path layers current-path)
          (tp/unchecked-above-sibling-path current-path)))
      (tp/above-top-path layers))))

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

(defn- group-safe-paths?
  "若选择图层中包括嵌套路径（一个路径是另一个的子路径），则认为打组不安全.
  空组打组不安全. 不检测重复."
  [paths]
  (if (= 1 (count paths))
    true
    (loop [a paths]
      (if-not (seq a)
        true
        (let [current (first a)]
          (if (some #(tp/ancestor? current %)
                    paths)
            false
            (recur (rest a))))))))

;; 把 layer-ids 编入新的图层组
(rf/reg-event-fx
  store/app-id :oplog/new-layer-group
  (fn [cofx [_ record-id
             {:keys [selected-layer-ids
                     selected-layer-id]}
             ctx]]
    (when selected-layer-id
      (let [record (:record cofx)
            layers (record/layers record)
            selected-layer-ids (distinct (vec selected-layer-ids))
            selected-paths (mapv #(layer.path/get-path layers %) selected-layer-ids)]
        (if (group-safe-paths? selected-paths)
          (let [target-path (layer.path/get-path layers selected-layer-id)
                active-layer (layer.path/get-layer layers target-path)
                [_sorted-paths sorted-ids]
                (tp/sort-by-path selected-paths selected-layer-ids)
                [layers' children]
                (reduce
                  (fn [[ls cs] lid]
                    (if (= lid selected-layer-id)
                      ;; 活动图层先不删除，保留方便定位
                      [ls (conj cs active-layer)]
                      (let [path (layer.path/get-path ls lid)
                            [ls' removed]
                            (layer.path/remove-layer ls path)]
                        [ls' (conj cs removed)])))
                  [layers []]
                  sorted-ids)
                group (assoc (group/make-layer-group) :layers (vec children))
                ;; 更新后的活动位置，因为图层没删，这个保持 ok
                target-path' (layer.path/get-path layers' selected-layer-id)
                layers'' (layer.path/update-layer layers' target-path' (constantly group))
                op (changes.layer/new-layer-group layers'')]
            {:dispatch [:oplog/log record-id op ctx]})
          {:fx [[:warn "Layer with its group cannot be grouped together"]]})))))

(defn- focus-layer-after-delete
  "1. 先找第一个在删除列表中的祖先，包括自己，定位真正的删除焦点.
   2. 从焦点开始向前找第一个不在删除列表中的 below-sibling，找到就返回.
   3. 找不到则回退到焦点的父路径，如果没有父了返回 nil."
  [layers active-path paths-to-delete]
  (when (and active-path (seq paths-to-delete))
    (let [delete-set (set paths-to-delete)
          ;; 1. 沿祖先链找第一个在删除列表中的路径（从自己往上）
          focus (loop [p active-path]
                  (when p
                    (if (delete-set p)
                      p
                      (let [pp (tp/parent p)]
                        (when (seq pp)
                          (recur pp))))))]
      (when focus
        (let [;; 2. 从焦点开始，逐个往前找兄弟，直到找到一个不在删除列表中的
              prev-sibling (loop [p focus]
                             (let [prev (tp/below-sibling-path layers p)]
                               (cond
                                 (nil? prev) nil
                                 (not (delete-set prev)) prev
                                 :else (recur prev))))]
          (if prev-sibling
            prev-sibling
            ;; 3. 所有前兄弟都被删了 → 找父路径
            (let [pp (tp/parent focus)]
              ;; 父路径为根容器 []时, 返回 nil
              (when (seq pp) pp))))))))

;; 删除所有选择图层
;; 删除后 1. 选择 selected-layer-id 第一个不在
(rf/reg-event-fx
  store/app-id :oplog/delete-selected-layers
  (fn [cofx [_ record-id
             {:keys [selected-layer-ids
                     selected-layer-id]}
             ctx]]
    (when selected-layer-id
      (let [record (:record cofx)
            layers (record/layers record)
            current-layer-id (record/current-layer-id record)
            active-path (layer.path/get-path layers selected-layer-id)
            paths-to-delete
            (tp/prune-subpaths
              (mapv #(layer.path/get-path layers %) selected-layer-ids))
            current-layer-deleted?
            (when current-layer-id
              (let [current-layer-path (layer.path/get-path layers current-layer-id)]
                (some
                  #(tp/within? current-layer-path %)
                  paths-to-delete)))
            ;; 转换成稳定的id
            layer-id-to-focus
            (when current-layer-deleted?
              (let [path-to-focus (focus-layer-after-delete layers active-path paths-to-delete)]
                (layer.core/layer-id (layer.path/get-layer layers path-to-focus))))
            layers'
            (reduce
              (fn [acc p]
                (first (layer.path/remove-layer acc p)))
              layers
              paths-to-delete)
            op (changes.layer/delete-layer
                 ;; 新图层替代，需要一次COW
                 record-id (clone-layer/clone-layers layers') paths-to-delete
                 current-layer-deleted? layer-id-to-focus)]
        {:dispatch [:oplog/log record-id op ctx]}))))

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