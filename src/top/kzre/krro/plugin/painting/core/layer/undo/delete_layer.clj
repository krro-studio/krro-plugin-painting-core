(ns top.kzre.krro.plugin.painting.core.layer.undo.delete-layer
  "Delete layer 需要抽象化才行,和打组不同，删除可能删除图层组.
  请在删除图层前 获取 undo 记录 record-data"
  (:require [clojure.set :as set]
            [taoensso.timbre :as log]
            [top.kzre.krro.canvas.core.layer.core :as layer.core]
            [top.kzre.krro.canvas.core.layer.path :as layer.path]
            [top.kzre.krro.plugin.painting.core.layer.tiles :as tiles]
            [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
            [top.kzre.krro.plugin.painting.core.record :as record]
            [top.kzre.krro.plugin.painting.core.store :as store]
            [top.kzre.krro.plugin.undo.core :as undo]))

(defn- undo-key [] ::delete-layer)

(defprotocol IDeleteLayerUndo
  (record-data [_ canvas-id layers layer-path]
    "返回undo redo时候用的记录数据, layers 结构是持久化的在undo redo 记录时候保持一致.
    返回 metadata")
  (undo-fx! [_ canvas-id data]
    "undo时候的副作用执行. 在 cb后执行")
  (redo-fx! [_ canvas-id data]
    "redo时候的副作用执行,在 cb 前执行"))

(def ^:private undo-registry (atom {}))

(defn reg-undo [layer-type undo]
  {:pre [(some? layer-type)
         (satisfies? IDeleteLayerUndo undo)]}
  (swap! undo-registry assoc layer-type undo))

(defn- get-undo [layer-type]
  (get @undo-registry layer-type))

(defn- require-undo [layer-type]
  (if-let [undo (get-undo layer-type)]
    undo
    (throw (ex-info "IDeleteLayerUndo for layer type not found"
                    {:type layer-type}))))

(defrecord DeleteGroupUndo []
  IDeleteLayerUndo
  (record-data [_ canvas-id layers layer-path]
    ;; 递归收集每个子节点的 record-data
    ;; 返回 [{:layer-type :raster :data ...} ...]
    (mapv (fn [child-path]
            (let [layer (layer.path/get-layer layers child-path)
                  type (layer.core/layer-type layer)
                  undo (require-undo type)]
              {:layer-type type
               :path child-path
               :data (record-data undo canvas-id layers child-path)}))
          (layer.path/child-paths layers layer-path)))

  (undo-fx! [_ canvas-id data]
    (doseq [{:keys [layer-type data]} (reverse data)]
      (let [undo (require-undo layer-type)]
        (undo-fx! undo canvas-id data))))

  (redo-fx! [_ canvas-id data]
    ;; 重做删除：子→父
    (doseq [{:keys [layer-type data]} data]
      (let [undo (require-undo layer-type)]
        (redo-fx! undo canvas-id data)))))

(defrecord DeletePersistentLayerUndo []
  IDeleteLayerUndo
  (record-data [_ _canvas-id _layers _layer-path] {})
  (undo-fx! [_ _canvas-id _data])
  (redo-fx! [_ _canvas-id _data]))

(defn undo-metadata
  "图层删除前记录。支持单路径或批量路径。
   paths 可以是单个路径向量或路径集合。"
  [canvas-id paths]
  (let [layers (-> (record/record canvas-id)
                   (record/layers))
        _ (doseq [p paths]
            (when-not (layer.path/get-layer layers p)
              (throw (ex-info "layer path not found, undo-metadata must call before delete executed."
                              {:canvas-id canvas-id
                               :layer-path p}))))
        ;; 每个路径收集 metadata
        per-path-meta (mapv (fn [layer-path]
                              (let [layer (layer.path/get-layer layers layer-path)
                                    type (layer.core/layer-type layer)
                                    dirty-tiles (tiles/global-tiles layers layer-path pc/global-tile-size)
                                    undo (require-undo type)]
                                {:layer-path  layer-path
                                 :data        (record-data undo canvas-id layers layer-path)
                                 :layer-type  type
                                 :dirty-tiles dirty-tiles
                                 :full-dirty? (nil? dirty-tiles)}))
                            paths)
        full-dirty? (some #(:full-dirty? %) per-path-meta)
        ;; 合并所有 dirty-tiles
        all-dirty-tiles
        (when-not full-dirty?
          (apply set/union (map :dirty-tiles per-path-meta)))]
    {:per-path      per-path-meta
     :dirty-tiles   all-dirty-tiles
     :full-dirty? full-dirty?
     :canvas-id     canvas-id
     :key           (undo-key)}))

(defn record! [{:keys [key] :as metadata}]
  (when-not (= key (undo-key))
    (throw (ex-info "Invalid metadata" {:metadata metadata})))
  (undo/record-fx! (undo-key) metadata))

(undo/reg-undo-fx
  (undo-key)
  {:undo
   (fn [{:keys [old-metadata]} cb]
     (log/info "undo delete-layer (batch)")
     (let [{:keys [canvas-id per-path dirty-tiles]} old-metadata]
       ;; 按路径逆序恢复（父→子，后删的先恢复）
       (doseq [{:keys [layer-type data]} (reverse per-path)]
         (let [undo (require-undo layer-type)]
           (undo-fx! undo canvas-id data)))
       (cb)
       (store/dispatch
         [:refresh-canvas canvas-id dirty-tiles])))

   :redo
   (fn [{:keys [new-metadata]} cb]
     (log/info "redo delete-layer (batch)")
     (let [{:keys [canvas-id per-path dirty-tiles]} new-metadata]
       (cb)
       ;; 按路径原序重删（子→父，先删子）
       (doseq [{:keys [layer-type data]} per-path]
         (let [undo (require-undo layer-type)]
           (redo-fx! undo canvas-id data)))

       (store/dispatch
         [:refresh-canvas canvas-id dirty-tiles])))})