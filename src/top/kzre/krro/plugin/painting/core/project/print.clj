(ns top.kzre.krro.plugin.painting.core.project.print
  "打印标准，这将可能影响我们导出图片，动画，或者预览。

  - 出血（bleed）是裁切边外侧预留的扩展绘制区域。
  印刷品裁切存在物理误差，画面必须溢出到裁切线外才能保证边缘不露白。
  - 安全区（safe-margin）是裁切边内侧的保留边界，
  文字和关键画面必须放在安全区内，避免被裁掉或因装订弯曲被遮挡。

  导出时候，可选的，超出出血先的范围会被裁剪掉。
  实际的内容大小是 canvas-size - bleed.")

(defrecord PrintSpec
  [;; ── 出血 ──
   bleed-top
   bleed-bottom
   bleed-left
   bleed-right

   ;; ── 安全区 ──
   safe-margin-top                                              ;; 安全区距裁切边的距离
   safe-margin-bottom
   safe-margin-left
   safe-margin-right
   ])
