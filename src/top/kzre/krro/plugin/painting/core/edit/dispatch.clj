(ns top.kzre.krro.plugin.painting.core.edit.dispatch
  (:require
   [top.kzre.krro.core.reframe.core :as rf]
   [top.kzre.krro.plugin.painting.core.edit.brush :as brush]
   [top.kzre.krro.plugin.painting.core.edit.anchor.state :as anchor]
   [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [tool-context-interceptor]]
   [top.kzre.krro.plugin.painting.core.edit.tool :as tool :refer [cleanup-tool-interceptor]]
   [top.kzre.krro.plugin.painting.core.store :as store]
   [top.kzre.krro.plugin.painting.core.record :as record]))

(defn viewport-event [{:keys [device-type mouse-button]
                       :as event-map}]
  (when (and (= :pointer device-type)
             (or (= :middle mouse-button)
                 (nil? mouse-button)))
    (case (:type event-map)
      :press :viewport-pan/press
      :drag :viewport-pan/drag
      :scroll :viewport-pan/scroll
      nil)))

(defmulti tool-event
  (fn [current-tool event-map]
    (if (viewport-event event-map)
      :viewport
      current-tool)))

;; 抽取到UI
(defmethod tool-event :default [_ _] nil)

;; 分发工具事件
(rf/reg-event-fx
  store/app-id :tool/dispatch-event
  (tool/dispatch-fn tool-event))

(rf/reg-event-fx
  store/app-id :tool/select-tool
  [(cleanup-tool-interceptor)]
  (fn [cofx [_ _ tool-id]]
    {:record
     (-> (:record cofx)
         (assoc-in [:tool-data]
                   (case tool-id
                     ;; 默认状态
                     :anchor (anchor/make-state)
                     :brush (brush/make-state)
                     nil))
         (record/assoc-current-tool tool-id))}))


(rf/reg-event-fx
  store/app-id :tool/flush-overlay
  [(tool-context-interceptor)]
  (fn [cofx [_ _ _ frame]]
    (let [ctx (:krro.painting/tool-context cofx)
          record (:record cofx)
          tool-data (get-in record [:tool-data])]
      (when tool-data
        {:fx [[:tool/flush-overlay
               (tool/overlay tool-data ctx)
               frame]]}))))