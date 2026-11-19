(ns top.kzre.krro.plugin.painting.core.config
  "全局配置项表,不用 defcustom, 这个是程序配置, 配置属于app 数据")

;; 运行时配置
(def  ^:dynamic *config* {})

(def ^:private default-config*
  {
   ;; 锚点不连续的颜色
   :anchor-corner-color [0 1 1 1]
   ;; 锚点平滑的颜色
   :anchor-smooth-color [0 0 1 1]
   ;; 活动锚点的颜色
   :anchor-active-color [1 1 0 0.9]
   ;; 锚点被选择时候的颜色
   :anchor-selected-color [1 1 0 0.8]
   ;; 活动锚点外框的颜色
   :anchor-active-frame-color [1 1 0 1]
   ;; 锚点被选择时候外框的颜色
   :anchor-selected-frame-color [0 0 1 1]
   ;; 一般时候锚点外框的颜色
   :anchor-frame-color [0 0 1 1]
   })

(defn default-config []
  default-config*)

(defn get-config
  "从 cfg 获取配置，否则从默认配置获取"
  ([kw] (get-config *config* kw))
  ([cfg kw]
   (or (get cfg kw)
       (get default-config* kw))))

(defn get-in-config
  ([kw] (get-in-config *config* kw))
  ([cfg path]
   (or (get-in cfg path)
       (get-in default-config* path))))

