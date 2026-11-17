(ns top.kzre.krro.plugin.painting.core.canvas.undo.edit-canvas
  "持久化数据获得变更的记录，刷新一次UI, 刷新一次渲染"
  (:require [top.kzre.krro.plugin.undo.core :as undo]
            [top.kzre.krro.core.reframe.core :as rf]
            [top.kzre.krro.plugin.painting.core.store :as store]))

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
       (rf/dispatch store/app-id [:refresh-canvas canvas-id]))
     )
   :redo
   (fn [{:keys [old-metadata]} cb]
     (let [{:keys [canvas-id]} old-metadata]
       (cb)
       (rf/dispatch store/app-id [:refresh-canvas canvas-id]))
     )})