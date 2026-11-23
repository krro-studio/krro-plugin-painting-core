(ns top.kzre.krro.plugin.painting.core.canvas.effects
  "画布相关的副作用处理器"
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.core.core :as kcc]
    [top.kzre.krro.core.hook :as hook]
    [top.kzre.krro.core.message :as msg]
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.layer.destroy :as destroy]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.state :as state]
    [top.kzre.krro.plugin.painting.core.store :as store]

    [top.kzre.krro.core.window :as win]
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.plugin.painting.core.spec :as spec]))

(rf/reg-fx
  :krro.painting :log-info
  (fn [_app-id message]
    (log/info message)))

(rf/reg-fx
  store/app-id :message
  (fn [_ message]
    (msg/message message)))

(rf/reg-fx
  store/app-id :warn
  (fn [_ message]
    (msg/warn message)))

(rf/reg-fx
  store/app-id :error
  (fn [_ message]
    (msg/error message)))


;; 刷新UI
(rf/reg-fx
  store/app-id :rerender-canvas-frame
  (fn [_ record-id]
    (let [frames (win/all-frames)
          filtered (filter #(= (frame/param % spec/canvas-id-key) record-id) frames)]
      (doseq [f filtered]
        (kcc/rerender! f)))))

;; 刷新画布
(rf/reg-fx
  store/app-id :render-canvas
  (fn render-canvas [_ record-id changes & ignored]
    (when ignored
      (log/warn "deprecated render-canvas call!"))
    (let [changes
          (cond
            (vector? changes) changes
            (map? changes) [changes]
            :else nil)]
      (when (seq changes)
        (let [cd (pc/canvas-data! record-id)]
          (hook/run-hook! :krro.painting/render-canvas-hook record-id cd changes))) )))


(rf/reg-fx
  store/app-id :close-canvas
  (fn [_ record-id]
    ;; 释放画布数据
    (let [cd (pc/canvas-data! record-id)
          layers (:layers cd)]
      (doseq [l layers]
        (destroy/destroy-layer l))
      ;(store/unreg-canvas-store record-id)
      )))
