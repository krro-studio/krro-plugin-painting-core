(ns top.kzre.krro.plugin.painting.core.oplog.layer
  (:require
   [top.kzre.krro.canvas.core.layer.core :as layer.core]
   [top.kzre.krro.core.reframe.core :as rf]
   [top.kzre.krro.plugin.painting.core.changes.layer :as changes.layer]
   [top.kzre.krro.plugin.painting.core.record :as record]
   [top.kzre.krro.plugin.painting.core.store :as store]))

(rf/reg-event-fx
  store/app-id :oplog/toggle-layer-visibility
  (fn [cofx [_ record-id layer-id ctx]]
    (let [{:keys [layer layer-transform]}
          (record/layer-context (:record cofx) layer-id)
          op (changes.layer/make-layer-visible-changed
               layer
               (not (boolean (layer.core/visible? layer)))
               layer-transform)]
      {:dispatch [:oplog/log record-id op ctx]})))
