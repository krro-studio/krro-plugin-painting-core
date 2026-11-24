(ns top.kzre.krro.plugin.painting.core.canvas.undo.edit-canvas
  "持久化数据获得变更的记录，刷新一次UI, 刷新一次渲染"
  (:require [top.kzre.krro.plugin.painting.core.store :as store]
            [top.kzre.krro.plugin.undo.core :as undo]))

(defn- undo-key [] ::edit-canvas)

(defn record-edit-canvas!
  "记录画布的持久化数据发生编程，重新渲染相关显示"
  [canvas-id]
  (undo/record-fx!
    (undo-key)
    {:canvas-id canvas-id}))

(undo/reg-undo-fx
  (undo-key)
  {:undo
   (fn [{:keys [old-metadata]} cb]
     (let [{:keys [canvas-id]} old-metadata]
       (cb)
       (store/dispatch
         [:refresh-canvas canvas-id]))
     )
   :redo
   (fn [{:keys [new-metadata]} cb]
     (let [{:keys [canvas-id]} new-metadata]
       (cb)
       (store/dispatch
         [:refresh-canvas canvas-id]))
     )})