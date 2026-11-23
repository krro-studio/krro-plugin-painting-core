(ns top.kzre.krro.plugin.painting.core.project.layer)

(defn ^:deprecated opacity
  "返回图层的不透明度。"
  [layer]
  (:opacity layer 1.0))

(defn ^:deprecated blend-mode
  "返回图层的混合模式, 默认是 normal 合成"
  [layer]
  (:blend-mode layer :normal))

(defn ^:deprecated visible?
  "返回图层是否可见。"
  [layer]
  (:visible? layer true))

(defn group-expanded?
  "图层是图层组，并且标记为展开状态。"
  [layer]
  (and (= :group (:type layer))
       (:expanded? layer)))

(defn layer-name
  [layer]
  (:name layer "Unnamed Layer"))

(defn locked?
  "图层是否被锁定，不可编辑"
  [layer]
  (:locked? layer false))

(defn alpha-locked?
  "光栅等可编辑图层专属, 像素锁定，完全透明区域不可编辑"
  [layer]
  (:alpha-locked? layer false))
