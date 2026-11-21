(ns top.kzre.krro.plugin.painting.core.project.comic
  "漫画工程"
  (:import (top.kzre.krro.plugin.painting.core.project.print PrintSpec)))

(defrecord ComicData
  [id
   title
   author
   created-at
   canvas-size                                              ;; 预设的画布尺寸
   canvas-size-align?                                       ;; 是否强制画布数据对齐(新创建页面时将不会询问画布大小)
   pages                                                    ;; 有序的页面向量.
   kind                                                     ;; 固定为 page
  ^PrintSpec print-spec
   ])

(defrecord ComicPage
  [canvas-id
   label                                                    ;; label 标记的文字
   ])

;; 条漫
(defrecord ScrollComicData
  [id
   canvas-id                                                ;; 条漫一般是一张画布
   kind                                                     ;; 固定为 :scroll
   frames
   canvas-width                                             ;; 宽度，和页漫不同，这个一定是强制对齐的
   canvas-height                                            ;; 高度不一定对齐
   canvas-height-align?                                     ;; 是否强制高度对齐,如果启用这个，那 frames 将毫无意义
   title
   author
   created-at
   ^PrintSpec  print-spec                                               ;; 条漫虽然实际不打印，但这作为我们导出规范还是有用的
   ])

;; 条漫的单格
(defrecord ScrollComicFrame
  [start-y                                                  ;;起始y坐标
   end-y                                                    ;; 末尾y坐标
   label                                                    ;; label 标记的文字
   ])

;; 一系列ComicData的集合, 不持有 ComicData, 只是一个管理层级
;; CanvasData 是一话，这里是一部的概念，包括若干话
(defrecord ComicProj
  [id                                                       ;;
   title                                                    ;; 标题
   chapters                                                 ;; 话，有序向量
   created-at
   ^PrintSpec print-spec
   ])