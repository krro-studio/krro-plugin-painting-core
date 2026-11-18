(ns top.kzre.krro.plugin.painting.core.edit.anchor.immediate
  "立即操作命令的派发"
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.edit.anchor.state :as anchor.state ]
    [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [get-tool-context
                                                                  tool-context-interceptor]]
    [top.kzre.krro.plugin.painting.core.edit.tool :refer [cleanup-tool-interceptor]]
    [top.kzre.krro.plugin.painting.core.store :as store])
  (:import
    (top.kzre.krro.plugin.painting.core.edit.anchor.state AnchorState)))


(rf/reg-event-fx
  store/app-id :anchor-delete/process
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-state)))
   (tool-context-interceptor)]
  (fn [cofx [_ record-id _event-map _frame]]
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
        {:dispatch [:oplog/anchor-delete
                    record-id layer-id selected-anchors
                    :undo? true]}))))



