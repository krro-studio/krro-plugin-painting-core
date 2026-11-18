(ns top.kzre.krro.plugin.painting.core.edit.anchor.fit-segment-free-modal
  "段自由拟合模态的事件入口：
     :anchor-fit-segment-free/trigger —— begin（挤出提交后分派进入）
     :anchor/fit-segment-free             —— move 预览
     :anchor/exit-fit-segment-free-modal  —— release 提交（单击）

   调整刚挤出的那一段使其经过光标。控制点数不变，锚点位置不动，
   只重排段内两个手柄。"
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.core.reframe.transaction :as tx :refer [transaction-interceptor]]
    [top.kzre.krro.plugin.painting.core.edit.anchor.state :as anchor.state]
    [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [get-tool-context
                                                                  tool-context-interceptor update-tool-context]]
    [top.kzre.krro.plugin.painting.core.edit.tool :refer [cleanup-tool-interceptor]]
    [top.kzre.krro.plugin.painting.core.edit.tool :as tool]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.plugin.painting.core.transactions.anchor-fit-segment-free-modal
     :as anchor-fit-modal])
  (:import
   (top.kzre.krro.plugin.painting.core.edit.anchor.state AnchorState)))

;; 指定的进入自由拟合，只支持 anchor 触发
;; anchor 必须是已经创建的锚点
(rf/reg-event-fx
  store/app-id :anchor-fit-segment-free/trigger
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-state)))
   (tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx [_ _ _ frame {:keys [anchor]}]]
    (let [{:keys [layer-type transaction-kind]} (get-tool-context cofx)]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}

        (nil? anchor)
        {:fx [[:warn "No anchor to fit"]]}

        (some? transaction-kind)
        {:fx [[:warn "Transaction already exists!"]]}

        :else
        (let [record (:record cofx)
              ctx (get-tool-context cofx)
              new-record
              (-> record
                  ;; 自动进入选择并激活
                  (assoc-in [:tool-data :active-anchor] anchor)
                  (update-in [:tool-data :selected-anchors]
                             (fn [selected]
                               (conj (or selected #{}) anchor)))
                  (assoc-in [:tool-data :mode]  :fit-segment-free)
                  (assoc-in [:tool-data :modal] true))]
          {:record new-record
           :transaction
           (tx/tx-begin
             (anchor-fit-modal/kind)
             {:anchor anchor})
           :fx [[:tool/flush-overlay
                 (tool/overlay (get new-record :tool-data)
                               (update-tool-context ctx new-record))
                 frame]]})))))

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
