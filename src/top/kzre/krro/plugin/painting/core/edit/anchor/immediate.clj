(ns top.kzre.krro.plugin.painting.core.edit.anchor.immediate
  "立即操作命令的派发
  一般动词规则是，
  - press, drag, release UI  入口
  - trigger, process 程序入口. 两个仅仅语气不同"
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.edit.anchor.state :as anchor.state ]
    [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [get-tool-context
                                                                  update-tool-context
                                                                  tool-context-interceptor]]
    [top.kzre.krro.plugin.painting.core.edit.tool :as tool :refer [cleanup-tool-interceptor]]
    [top.kzre.krro.plugin.painting.core.store :as store])
  (:import
    (top.kzre.krro.plugin.painting.core.edit.anchor.state AnchorState)))


(rf/reg-event-fx
  store/app-id :anchor-delete/process
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-state)))
   (tool-context-interceptor)]
  (fn [cofx [_ record-id _event-map frame]]
    (let [ctx (get-tool-context cofx)
          {:keys [ layer-type layer-id]} ctx
          record (:record cofx)
          selected-anchors (get-in record [:tool-data :selected-anchors])]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}
        (empty? selected-anchors)
        {:fx [[:warn "No anchor selected"]]}
        :else
        {:record (->  record
                      (update :tool-data anchor.state/clear-selected-anchors))
         :dispatch [:oplog/anchor-delete
                    record-id layer-id selected-anchors
                    :undo? true

                    ;; 延迟到整个oplog流程完成后的副作用
                    :effects
                    ;; 闭包
                    (fn [final-record]
                      (let [final-ctx (update-tool-context ctx final-record)]
                        {:fx [[:tool/flush-overlay
                               (tool/overlay (get final-record :tool-data) final-ctx)
                               frame]]}))]}))))

;; 需要维护 rect-quadtree
(rf/reg-event-fx
  store/app-id :anchor-insert/process
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-state)))
   (tool-context-interceptor)]
  (fn [cofx [_ record-id _event-map frame]]
    (let [ctx (get-tool-context cofx)
          {:keys [ layer-type layer-id]} ctx
          record (:record cofx)
          selected-anchors (get-in record [:tool-data :selected-anchors])]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}
        (empty? selected-anchors)
        {:fx [[:warn "No anchor selected"]]}
        :else
        {:record (->  record
                      (update :tool-data anchor.state/clear-selected-anchors))
         :dispatch [:oplog/anchor-delete
                    record-id layer-id selected-anchors
                    :undo? true

                    ;; 延迟到整个oplog流程完成后的副作用
                    :effects
                    ;; 闭包
                    (fn [final-record]
                      (let [final-ctx (update-tool-context ctx final-record)]
                        {:fx [[:tool/flush-overlay
                               (tool/overlay (get final-record :tool-data) final-ctx)
                               frame]]}))]}))))
