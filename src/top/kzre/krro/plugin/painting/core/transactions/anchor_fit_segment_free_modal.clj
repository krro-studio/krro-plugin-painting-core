(ns top.kzre.krro.plugin.painting.core.transactions.anchor-fit-segment-free-modal
  "段自由拟合模态事务。

   进入时备份图层并定下要拟合的段（挤出端点外侧那一段）——
   每次 move 从备份重算拟合（幂等，手柄不逐帧累积）——
   commit 打 undo 标记。控制点数量始终不变。"
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.canvas.vector.core :as cv]
    [top.kzre.krro.core.reframe.transaction :as tx]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.transactions.vector-layer :as tx.vector-layer])
  (:import
    (top.kzre.krro.canvas.vector.anchor Anchor)))

(defonce ^:private kind* ::anchor-fit-segment-free-modal)
(defn kind [] kind*)

(defn- outer-seg-idx
  "端点锚点外侧那一段的段号——首点对 0 段，末点对最后一段。"
  [^Anchor anchor]
  (if (zero? (:point-idx anchor)) 0 (dec (:point-idx anchor))))

(defn- process-fit
  [{:keys [layer-backup path-id seg-idx] :as tx} layer-event record commit?]
  (let [{:keys [canvas-id layer-id]} (record/layer-context record)
        effects
        {:dispatch [:oplog/fit-segment-free
                    canvas-id layer-id path-id seg-idx
                    (select-keys layer-event [:x :y])
                    :paths (cv/paths layer-backup)
                    :undo? commit?]
         :fx [[:tool/set-command-enabled commit?]]}]
    (if commit?
      effects
      [tx effects])))

(defrecord AnchorFitSegmentFreeModalTransaction
  [layer-backup            ; 进入时的 layer 快照——拟合基准 + rollback 用
   path-id                 ; 被拟合路径
   seg-idx]                ; 被拟合段
  tx/ITransaction
  (kind [_] (kind))

  (begin [this {:keys [^Anchor anchor
                       segment]} record]
    ;; 通过边缘顶点指定，或者指定段
    (let [by-anchor? (some? anchor)
          by-seg? (and (not by-anchor?)
                       (some? segment))
          {:keys [canvas-id layer-id layer]} (record/layer-context record)
          paths   (cv/paths layer)
          path-id (:path-id (or anchor segment))
          path    (get paths path-id)]
      (cond
        (nil? path)
        (do (log/info "fit-segment-free: path not found"
                      {:canvas-id canvas-id :layer-id layer-id :anchor anchor})
            [nil {}])

        (< (count (cv/path-points path)) 2)
        (do (log/info "fit-segment-free: path has no segment to fit"
                      {:canvas-id canvas-id
                       :layer-id  layer-id
                       :path-id   (:path-id anchor)})
            [nil {}])

        ;; 锚点指定只支持端点
        (and by-anchor?
             (not (cv/end-anchor? paths anchor)))
        (do (log/info "fit-segment-free: anchor is not an endpoint"
                      {:canvas-id canvas-id :layer-id layer-id :anchor anchor})
            [nil {}])

        :else
        [(assoc this
           :layer-backup layer
           :path-id      path-id
           :seg-idx (cond
                      by-anchor? (outer-seg-idx anchor)
                      by-seg? (:seg-idx segment)))
         {:fx [[:tool/set-command-enabled false]]}])))

  (operate [this op-kind {:keys [layer-event]} record]
    (case op-kind
      :move
      (process-fit this layer-event record false)

      (throw (ex-info "unknown op-kind" {:op-kind op-kind :kind (kind)}))))

  (commit [this {:keys [layer-event]} record]
    (process-fit this layer-event record true))

  (rollback [_ _ record]
    (tx.vector-layer/rollback-effect record layer-backup)))

(tx/reg-transaction (kind)
                    (map->AnchorFitSegmentFreeModalTransaction {}))
