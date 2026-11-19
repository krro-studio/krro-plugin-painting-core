(ns top.kzre.krro.plugin.painting.core.edit.anchor.anchor-extrude-modal
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.core.reframe.transaction :as tx :refer [transaction-interceptor]]
    [top.kzre.krro.plugin.painting.core.edit.anchor.state :as anchor.state]
    [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [get-tool-context
                                                                  tool-context-interceptor]]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.plugin.painting.core.edit.tool :refer [cleanup-tool-interceptor]]
    [top.kzre.krro.plugin.painting.core.transactions.anchor-extrude-modal :as anchor-extrude-modal])
  (:import (top.kzre.krro.plugin.painting.core.edit.anchor.state AnchorState)))

;; 进入顶点挤出模态
(rf/reg-event-fx
  store/app-id :anchor/enter-extrude-anchor-modal
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-state)))
   (tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx _]
    (let [ctx       (get-tool-context cofx)
          {:keys [layer-type layer-event transaction-kind]} ctx
          tool-data (get-in (:record cofx) [ :tool-data])
          {:keys [active-anchor]} tool-data]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}

        (nil? active-anchor)
        {:fx [[:warn "No active anchor"]]}

        (some? transaction-kind)
        {:fx [[:warn "Transaction already exists!"]]}

        :else
        {:record (-> (:record cofx)
                     (assoc-in [ :tool-data :mode]  :extrude-anchor)
                     (assoc-in [ :tool-data :modal] true))
         :transaction
         (tx/tx-begin
           (anchor-extrude-modal/kind)
           {:anchor active-anchor
            :layer-event layer-event})}))))

;; 挤出顶点 —— move 阶段
(rf/reg-event-fx
  store/app-id :anchor/extrude-anchor
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-state)))
   (tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx [_ _record-id _ frame]]
    (let [ctx (get-tool-context cofx)
          {:keys [layer-event layer-type transaction-kind]} ctx]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}

        (not= transaction-kind (anchor-extrude-modal/kind))
        {:fx [[:warn "No active extrude transaction"]]}

        :else
        {:transaction
         [(tx/transaction-operation
            (anchor-extrude-modal/kind)
            :move
            :layer-event layer-event)]}))))

;; 退出锚点挤出模态 —— commit
;; 挤出只是把新锚点放到光标处，新段还是直的——
;; 提交后分派独立事件进入段自由拟合模态：新段跟随光标弯出，单击提交。
;; 拟合模态不在本事件的 :transaction 里 begin——事务指令先于 reframe 结果处理执行，
;; 连续两条指令时 commit 产生的变更还没落库，begin 会读到旧 record。
(rf/reg-event-fx
  store/app-id :anchor/exit-extrude-anchor-modal
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-state)))
   (tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx _]
    (let [ctx (get-tool-context cofx)
          {:keys [layer-event layer-type transaction transaction-kind]} ctx
          [_ record-id event-map frame] (:event cofx)]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}

        (not= transaction-kind (anchor-extrude-modal/kind))
        {:fx [[:warn "No active extrude transaction"]]}

        :else
        {:record (-> (:record cofx)
                     (assoc-in [:tool-data :mode]  nil)
                     (assoc-in [:tool-data :modal] false))
         :transaction
         (tx/tx-commit
           (anchor-extrude-modal/kind)
           {:effects
            ;; 延迟到整个事务 oplog 完成触发的副作用
            {:dispatch [:anchor-fit-segment-free/trigger
                        record-id event-map frame
                        {:anchor (:new-anchor transaction)}]}})}))))