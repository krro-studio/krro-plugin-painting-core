(ns top.kzre.krro.plugin.painting.core.edit.anchor.fit-segment-free-modal
  "段自由拟合模态的事件入口：
     :anchor/enter-fit-segment-free-modal —— begin（挤出提交后分派进入）
     :anchor/fit-segment-free             —— move 预览
     :anchor/exit-fit-segment-free-modal  —— release 提交（单击）

   调整刚挤出的那一段使其经过光标。控制点数不变，锚点位置不动，
   只重排段内两个手柄。"
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.core.reframe.transaction :as tx :refer [transaction-interceptor]]
    [top.kzre.krro.plugin.painting.core.edit.anchor.state :as anchor.state]
    [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [get-tool-context
                                                                  tool-context-interceptor]]
    [top.kzre.krro.plugin.painting.core.edit.tool :refer [cleanup-tool-interceptor]]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.plugin.painting.core.transactions.anchor-fit-segment-free-modal
     :as anchor-fit-modal])
  (:import (top.kzre.krro.plugin.painting.core.edit.anchor.state AnchorState)))

;; 进入段自由拟合模态
;; 由 :anchor/exit-extrude-anchor-modal 在挤出提交后单独分派——
;; 事务指令先于 reframe 结果处理执行，同一条事件里 commit + begin 会读到旧 record。
(rf/reg-event-fx
  store/app-id :anchor/enter-fit-segment-free-modal
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-state)))
   (tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx [_ _ _ _ anchor point]]
    (let [{:keys [layer-type transaction-kind]} (get-tool-context cofx)]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}

        (nil? anchor)
        {:fx [[:warn "No anchor to fit"]]}

        (some? transaction-kind)
        {:fx [[:warn "Transaction already exists!"]]}

        :else
        {:record (-> (:record cofx)
                     (assoc-in [ :tool-data :mode]  :fit-segment-free)
                     (assoc-in [ :tool-data :modal] true))
         :transaction
         [(tx/begin-transaction
            (anchor-fit-modal/kind)
            :anchor      anchor
            :layer-event point)]}))))

;; 拟合挤出段 —— move 阶段
(rf/reg-event-fx
  store/app-id :anchor/fit-segment-free
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-state)))
   (tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx _]
    (let [ctx (get-tool-context cofx)
          {:keys [layer-event layer-type transaction-kind]} ctx]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}

        (not= transaction-kind (anchor-fit-modal/kind))
        {:fx [[:warn "No active fit-segment-free transaction"]]}

        :else
        {:transaction
         [(tx/transaction-operation
            (anchor-fit-modal/kind)
            :move
            :layer-event layer-event)]}))))

;; 退出段拟合模态 —— commit
(rf/reg-event-fx
  store/app-id :anchor/exit-fit-segment-free-modal
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-state)))
   (tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx _]
    (let [ctx (get-tool-context cofx)
          {:keys [layer-event layer-type transaction-kind]} ctx]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}

        (not= transaction-kind (anchor-fit-modal/kind))
        {:fx [[:warn "No active fit-segment-free transaction"]]}

        :else
        {:record (-> (:record cofx)
                     (assoc-in [ :tool-data :mode]  nil)
                     (assoc-in [ :tool-data :modal] false))
         :transaction
         [(tx/commit-transaction
            (anchor-fit-modal/kind)
            :layer-event layer-event)]}))))
