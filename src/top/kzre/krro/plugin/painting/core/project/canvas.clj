(ns top.kzre.krro.plugin.painting.core.project.canvas
  "画布数据，负责定义总体画布结构，并提供多方法供图层拓展."
  (:require
    [clojure.spec.alpha :as s]
    [top.kzre.krro.canvas.core.layer.core :as lc]
    [top.kzre.krro.core.project :as proj]
    [top.kzre.krro.core.core :as kcc]
    [top.kzre.krro.core.rdb :refer [defschema]]
    [top.kzre.krro.canvas.core.layer.path :as path])
  (:import
   (java.util UUID)))

;; 全局瓦片大小 64x64
(defonce global-tile-size 64)

;; 水平镜像编辑
(s/def ::horizontal-mirror-enabled (s/nilable boolean?))
;; 垂直镜像编辑
(s/def ::vertical-mirror-enabled (s/nilable boolean?))
(s/def ::horizontal-mirror-center (s/nilable number?))
(s/def ::vertical-mirror-center (s/nilable number?))

(s/def ::tiling-enabled (s/nilable boolean?))

(s/def ::id keyword?)
(s/def ::current-layer-id (s/nilable keyword?))
(s/def ::width number?)
(s/def ::height number?)
(s/def ::layers vector?)
(s/def ::current-tool (s/nilable keyword?))
(s/def ::canvas-data
  (s/keys
    :req-un [::id ::width ::height ::layers]
    :opt-un [::current-layer-id
             ::current-tool
             ::horizontal-mirror-enabled
             ::horizontal-mirror-center
             ::vertical-mirror-enabled
             ::vertical-mirror-center
             ::tiling-enabled]))

;; TODO 会话数据不进CanvasData。关闭画布时候另外持久化一下
;; TODO 进CanvasData 唯一标准，你是否希望这个数据在undo/redo时候恢复
(defrecord CanvasData
  [id
   width
   height
   layers
   ;; 当前图层是重要属性，影响渲染，不作为会话数据管理
   current-layer-id
   ;; TODO 下面这些属于会话数据，不进项目数据中，虽然训词持久化，但不属于项目数据
   ;; 项目数据需要强一致性，我们不对下面的这些数据做这个保证，UNDO 时候也不会回退
   current-tool
   horizontal-mirror-enabled            ;; 可选，boolean? 或 nil
   horizontal-mirror-center             ;; 可选，number? 或 nil
   vertical-mirror-enabled              ;; 可选，boolean? 或 nil
   vertical-mirror-center               ;; 可选，number? 或 nil
   tiling-enabled])                     ;; 可选，boolean? 或 nil

(defschema :krro.painting/canvas
               :primary-key :id
               :spec ::canvas-data                          ;; 利用spec 进行 rdb 约束
               :not-null [:id :width :height :layers]
               :defaults {:layers []})

(defn make-canvas-data
  [width height & {:keys [id layers current-layer-id brush-tool-id
                          horizontal-mirror-enabled horizontal-mirror-center
                          vertical-mirror-enabled vertical-mirror-center tiling-enabled]
                   :or {id (keyword (str "canvas-" (UUID/randomUUID)))
                        layers []
                        horizontal-mirror-enabled false
                        horizontal-mirror-center 0
                        vertical-mirror-enabled false
                        vertical-mirror-center 0
                        tiling-enabled false
                        brush-tool-id :brush}}]
  (map->CanvasData
    {:id id
     :width width
     :height height
     :layers layers
     :current-layer-id current-layer-id
     :current-tool brush-tool-id
     :horizontal-mirror-enabled horizontal-mirror-enabled
     :horizontal-mirror-center horizontal-mirror-center
     :vertical-mirror-enabled vertical-mirror-enabled
     :vertical-mirror-center vertical-mirror-center
     :tiling-enabled tiling-enabled}))

(defn create-canvas!
  "创建空白画布"
  ([w h] (create-canvas! (keyword (str (UUID/randomUUID))) w h))
  ([id w h]
   (let [cd (make-canvas-data w h :id id)]   ;; 将测试图层放入 layers 向量
     (kcc/insert! :krro.painting/canvas (assoc cd :id id))
     cd)))

(defn insert-layer-at-path
  [^CanvasData cd path layer]
  (update cd :layers
          (fn [layers] (path/insert-layer layers path layer))))

(defn delete-canvas!
  "删除画布，相关资源由 rdb 负责级联删除."
  [id]
  (kcc/delete-by-id! :krro.painting/canvas id))

(defn save-canvas-data!
  "创建或更新画布数据"
  [canvas-id cd]
  (kcc/update-by-id! :krro.painting/canvas canvas-id (constantly cd)))

(defn canvas-data
  "查询画布数据，当查询的是非代理数据或确认数据已经激活使用允许使用."
  ^CanvasData
  ([canvas-id]
   (kcc/select-by-id :krro.painting/canvas canvas-id))
  ([canvas-id db-map]                                              ;; 从指定map查询数据，用于双向绑定检查更新
   (get-in db-map [:krro.painting/canvas canvas-id])))

(defn canvas-data!
  "查询并激活画布数据."
  ^CanvasData [canvas-id]
  (proj/get-in-project! [:krro.painting/canvas canvas-id]))

(defn ensure-canvas-data!
  "确保画布存在并返回激活的 CanvasData。"
  [canvas-id width height]
  (or (canvas-data! canvas-id)
      (create-canvas! canvas-id width height)))

(defn canvas-size
  "查询画布尺寸，这两个是非代理资源，保持键一致就能多态获取了,无需激活."
  [canvas-id]
  (when-let [cd (canvas-data! canvas-id)]
    [(:width cd) (:height cd)]))

(defn deactivate-canvas!
  "将 CanvasData 编码回代理 map, 日后拓展虚拟代理时候可以把画布数据保存到外部."
  [canvas-id]
  (proj/deactivate-resource! [:krro.painting/canvas canvas-id]))

(defn current-layer-id
  ([canvas-id]
   (when-let [cd (canvas-data canvas-id)]
     (:current-layer-id cd)))
  ([canvas-id db-map]
   (when-let [cd (canvas-data canvas-id db-map)]
     (:current-layer-id cd))))

;; 持久化多方法.
(defmulti persistable-layer :type)
(defmulti persistable-layer! (fn [layer _canvas-id] (:type layer)))
(defmethod persistable-layer! :default
  [layer _canvas-id] (persistable-layer layer))
(defmulti active-layer! (fn [layer _canvas-id] (:type layer)))

(defmethod persistable-layer :default [layer] layer)

(defmethod active-layer! :default [layer _canvas-id] layer)

(defonce ^:private canvas-data-resource-key ::canvas-data)

(kcc/reg-resource
  canvas-data-resource-key
  CanvasData
  (fn [^CanvasData canvas _ctx]
    (let [id (:id canvas)
          encoded-layers (mapv #(persistable-layer! % id) (:layers canvas))]
      {:krro/type canvas-data-resource-key
       :id id
       :width  (:width canvas)
       :height (:height canvas)
       :layers encoded-layers
       :current-layer-id (:current-layer-id canvas)}))
  (fn [m]
    (let [id (:id m)
          w (:width m)
          h (:height m)
          decoded-layers (mapv #(active-layer! % id) (:layers m))]
      (map->CanvasData {:id id :width w :height h :layers decoded-layers
                        :current-layer-id (:current-layer-id m)}))))

;; 其他低级查询
(defn layers-by-id
  ([canvas-id] (:layers (canvas-data canvas-id))))

(defn layers-by-id!
  ([canvas-id] (:layers (canvas-data! canvas-id))))

(defn find-layer-in-canvas! [canvas-id layer-id]
  (when-let [ls (layers-by-id! canvas-id)]
    (lc/find-layer layer-id ls)))

(defn visible-layer?
  ([canvas-id layer-id]
   (when-let [cd (canvas-data canvas-id)]
     (when-let [l (lc/find-layer layer-id (:layers cd))]
       (:visible? l))))
  ([canvas-id layer-id db-map]
   (when-let [cd (canvas-data canvas-id db-map)]
     (when-let [l (lc/find-layer layer-id (:layers cd))]
       (:visible? l)))))
