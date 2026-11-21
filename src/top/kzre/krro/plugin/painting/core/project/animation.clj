(ns top.kzre.krro.plugin.painting.core.project.animation
  "动画工程。一个动画片段（shot/clip），不是完整动画作品。
   产出为帧序列或视频片段。

   帧系统由两种帧组成：
   - CanvasFrame：切换画布，对应传统逐帧动画（每帧一张完整画面）
   - PropFrame：对画布上特定图层的特定属性设值，对应关键帧/曲线动画

   同一 frame-idx 下可同时存在 CanvasFrame 和若干 PropFrame，
   组合描述该时刻的完整画面状态。

   print-spec 为必选，决定导出时裁切范围与标题/动作安全区。"
  (:import (top.kzre.krro.plugin.painting.core.project.print PrintSpec)))


(defrecord AnimationData
  [id
   title
   created-at
   ^long fps                                                 ;; 基本FPS，标准日式标准为 24
   ^long length                                              ;; 时间长度, 单位是s
   frames                                                   ;; 帧map，frame-idx=>画布帧，属性帧或者属性帧的向量
   ^long view-width                                               ;; 视口宽度，像素
   ^long view-height                                              ;;视口高度，像素
   ])

;;画布帧, 切换为一个新的画布，只由这个帧表示的动画即为传统的帧动画
(defrecord CanvasFrame
  [kind                                                     ;; 固定为 canvas
   canvas-id                                                ;; 引用的动画id
   origin                                                   ;; 画布的原点偏移，这个问题主要是因为画布大小可能改变，而画布坐标追中[0, 0] 始终对应左上角，origin使得CameraFrame不会失效
   ^PrintSpec print-spec] ;; 出版规格
  "画布帧。该帧切换为一个新的画布。
 仅由此帧组成的动画即为传统逐帧动画（每一帧是一张完整绘制）。")

;; 属性帧,覆盖在 Canvas 上的设置，使用这个的动画即为关键帧动画或者曲线动画
(defrecord PropFrame
  [;; 通道控制的图层路径
   layer-id
   ;; 通道控制的图层属性路径
   prop-path
   value
   ;; 曲线控制点数据，当value 为数值类型时候用于曲线动画
   control-point
   ]
  "属性帧。覆盖在画布上的属性设置。
 该帧对指定图层的指定属性设值，配合插值形成关键帧动画或曲线动画。
 与传统逐帧动画混合使用时，CanvasFrame 提供底层画面，PropFrame 叠加变换/可见性等控制。")

(defrecord CameraFrame
  [position                                                  ;; [x y] 相机位置
   zoom                                                      ;; 缩放因子
   rotation                                                  ;; 旋转角度，逆时帧率为正
   ]
  "相机帧。定义该时刻的相机状态。
 动画的可见范围由相机决定，而非画布尺寸——
 画布可以远大于输出分辨率，相机决定取景框的位置、缩放、旋转。
 整体视频导出时的裁切规格由相机轨迹（CameraFrame 序列）决定。
 相机运镜（推拉摇移）即 CameraFrame 的插值轨迹。
 position 是相机左上角的位置.")

