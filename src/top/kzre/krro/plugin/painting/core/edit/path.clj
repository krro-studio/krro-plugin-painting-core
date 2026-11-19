(ns top.kzre.krro.plugin.painting.core.edit.path
  (:require [top.kzre.krro.plugin.painting.core.brush.core :as brush]))


(defn make-bezier-path
  [point]
  {:path-type :bezier
   :curve {:points [point]
           :closed? false}
   :style
   (let [brush-spec (brush/get-global-brush-color)]
     {:stroke {:color brush-spec
               :width (:radius brush-spec 15)
               :cap   :round
               :join  :round}})})