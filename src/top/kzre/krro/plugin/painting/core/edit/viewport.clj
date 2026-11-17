(ns top.kzre.krro.plugin.painting.core.edit.viewport
  (:require
   [top.kzre.krro.core.custom :as custom]
   [top.kzre.krro.core.reframe.core :as rf]
   [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [tool-context-interceptor]]
   [top.kzre.krro.plugin.painting.core.edit.tool :as tool ]
   [top.kzre.krro.plugin.painting.core.store :as store]
   [top.kzre.krro.plugin.painting.core.viewport :as vp]
   [top.kzre.krro.plugin.painting.core.changes.viewport :as viewport])
  (:import
    (top.kzre.krro.plugin.painting.core.edit.tool IToolData)))

(defn- viewport-pan-speed []
  :krro.painting/viewport-pan-speed)

(custom/defcustom (viewport-pan-speed)
                  1.0
                  :type :number
                  :group :krro.painting/edit
                  :doc "视口平移速度，1.0 为像素同比速度。")

(defn- viewport-pan-dead-zone []
  :krro.painting/viewport-pan-dead-zone)

(custom/defcustom (viewport-pan-dead-zone)
                  0.5
                  :type :number
                  :group :krro.painting/edit
                  :doc "视口平移死区（像素），位移小于此值时忽略移动。")

(defn- viewport-zoom-sensitivity-key []
  :krro.painting/viewport-zoom-sensitivity)

(custom/defcustom (viewport-zoom-sensitivity-key)
                  1.05
                  :type :number
                  :group :krro.painting/edit
                  :doc "滚轮缩放灵敏度，表示每一格 delta 的缩放因子。1.1 表示每格放大 10%。")

(defrecord ViewportState [init-cursor-x init-cursor-y original-viewport moving?]
  IToolData
  (cleanup! [_ _]
    nil)
  (overlay [_ _]
    nil)
  (dispatch-event [_ _ _] nil))

;; 设置视口
(rf/reg-fx
  store/app-id :set-viewport
  (fn [_ frame viewport]
    (vp/set-viewport! frame viewport)))

(rf/reg-event-fx
  store/app-id :viewport-pan/press
  (fn [cofx [_ _ {:keys [x y]} frame]]
    (let [original-viewport (vp/get-viewport frame)
          record (:record cofx)]
      {:record (assoc-in record [ :viewport-state]
                         (->ViewportState x y original-viewport true))})))


(rf/reg-event-fx
  store/app-id :viewport-pan/drag
  [(tool-context-interceptor)]
  (fn [cofx [_ record-id {:keys [x y] } frame]]
    (let [record (:record cofx)
          state (get-in record [ :viewport-state])]
      (when (instance? ViewportState state)
        (let [speed (custom/get-custom (viewport-pan-speed))
              dead-zone (custom/get-custom (viewport-pan-dead-zone))
              init-cursor-x (get-in record [ :viewport-state :init-cursor-x])
              init-cursor-y (get-in record [ :viewport-state :init-cursor-y])
              dx (* speed (- x init-cursor-x))
              dy (* speed (- y init-cursor-y))]
          (if (and (< (Math/abs (double dx)) dead-zone)
                   (< (Math/abs (double dy)) dead-zone))
            nil
            (let [tool-data (get-in record [ :tool-data])
                  original-viewport (get-in record [ :viewport-state :original-viewport])
                  zoom (:zoom original-viewport)
                  new-viewport (-> original-viewport
                                   (update-in [:offset-x] (fn [offset-x] (- offset-x (/ dx zoom))))
                                   (update-in [:offset-y] (fn [offset-y] (- offset-y (/ dy zoom)))))]
              {:fx [[:set-viewport frame new-viewport]
                    [:tool/flush-overlay
                     (when (and tool-data (satisfies? tool/IToolData tool-data))
                       (tool/overlay tool-data (:krro.painting/tool-context cofx)))
                     frame]
                    [:render-canvas record-id (viewport/refreshed)]]})))))))


(rf/reg-event-fx
  store/app-id :viewport-pan/scroll
  [(tool-context-interceptor)]
  (fn [cofx [_ record-id {:keys [x y delta-y]} frame]]
    (when delta-y
      (let [record (:record cofx)
            ctx (:krro.painting/tool-context cofx)
            {:keys [viewport canvas-event]} ctx
            sensitivity (custom/get-custom (viewport-zoom-sensitivity-key))
            old-zoom (:zoom viewport)
            new-zoom (-> old-zoom
                         (* (Math/pow sensitivity (double delta-y)))
                         (max 0.01) (min 100.0))
            ;; 鼠标指向的逻辑坐标（直接从 canvas-event 获取）
            cx (:x canvas-event)
            cy (:y canvas-event)
            new-offset-x (- cx (/ x new-zoom))
            new-offset-y (- cy (/ y new-zoom))
            new-viewport (assoc viewport
                           :zoom new-zoom
                           :offset-x new-offset-x
                           :offset-y new-offset-y)
            tool-data (get-in record [ :tool-data])
            viewport-state (get-in record [ :viewport-state])]
        {:record
         (when (and (instance? ViewportState viewport-state)
                    (:moving? viewport-state))
           (update-in record [ :viewport-state]
                      merge
                      {:init-cursor-x x
                       :init-cursor-y y
                       :original-viewport new-viewport}))
         :fx [[:set-viewport frame new-viewport]
              [:tool/flush-overlay
               (when (and tool-data (satisfies? tool/IToolData tool-data))
                 (tool/overlay tool-data (assoc ctx :viewport new-viewport)))
               frame]
              [:render-canvas record-id [(viewport/refreshed)] ]]}))))
