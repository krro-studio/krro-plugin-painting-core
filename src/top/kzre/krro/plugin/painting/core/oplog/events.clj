(ns top.kzre.krro.plugin.painting.core.oplog.events
  (:require
   [top.kzre.krro.core.reframe.core :as rf]
   [top.kzre.krro.core.reframe.util :as reframe.util]
   [top.kzre.krro.plugin.painting.core.oplog.protocol :as proto]
   [top.kzre.krro.plugin.painting.core.store :as store])
  (:import
   (java.lang AutoCloseable)
   (top.kzre.krro.plugin.painting.core.oplog.protocol IOperation)))


(rf/reg-event-fx
  store/app-id :oplog/log
  (fn [{:keys [record]}
       [_ record-id ^IOperation op
        {:keys [undo? effects]
         :or {undo? false}
         :as ctx}]]
    (try
      (let [[new-record fx] (proto/realize op record ctx)
            undo-effects
            (if undo?
              {:record new-record
               :fx     (conj (or fx []) [:oplog/record record-id op])}
              (do
                (when (instance? AutoCloseable op)
                  (.close ^AutoCloseable op))
                {:record new-record
                 :fx     fx}))]
        (if effects
          (reframe.util/merge-effects
            undo-effects
            ;; 支持延迟求值
            (if (ifn? effects) (effects new-record) effects))
          undo-effects))
      (catch Throwable e
        (when (instance? AutoCloseable op)
          (.close ^AutoCloseable op))
        (throw e)))))