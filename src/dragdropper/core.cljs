(ns draggable.core
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

;;******************************************************************************
;;  Draggable protocol
;;******************************************************************************

(defprotocol IDragStart
  (drag-start [this event]))

(defprotocol IDragMove
  (drag-move [this event]))

(defprotocol IDragEnd
  (drag-end [this event]))

;;******************************************************************************
;;  Various draggable implementations of dragging
;;******************************************************************************

(defn -drag-start [owner event]
  (let [node (om/get-node owner)
        rel-x (- (.-pageX event) (.-offsetLeft node))
        rel-y (- (.-pageY event) (.-offsetTop node))]
    (om/set-state! owner :dragging true)
    (om/set-state! owner :rel-x rel-x)
    (om/set-state! owner :rel-y rel-y)))

;;******************************************************************************
;;  - Free-drag
;;******************************************************************************

(defn free-drag [owner]
  (reify
    IDragStart
    (drag-start [_ event]
      (-drag-start owner event))
    IDragMove
    (drag-move [_ event]
      (when (om/get-state owner :dragging)
        (let [rel-y (om/get-state owner :rel-y)
              rel-x (om/get-state owner :rel-x)]
          (om/set-state! owner :ver-value (- (.. event -pageY) rel-y))
          (om/set-state! owner :hor-value (- (.. event -pageX) rel-x)))))
    IDragEnd
    (drag-end [_ event]
      (when (om/get-state owner :dragging)
        (let [rel-y (om/get-state owner :rel-y)
              rel-x (om/get-state owner :rel-x)]
          (om/set-state! owner :dragging false)
          (om/set-state! owner :ver-value (- (.. event -pageY) rel-y))
          (om/set-state! owner :hor-value (- (.. event -pageX) rel-x)))))))

;;******************************************************************************
;;   - Grid-snap-drag
;;******************************************************************************

(defn nearest-corner [cell-size x y w h]
  (let [mod-x (mod x cell-size)
        mod-y (mod y cell-size)
        mod-w (- cell-size (mod (+ x w) cell-size))
        mod-h (- cell-size (mod (+ y h) cell-size))
        off-x (js/Math.floor (/ x cell-size))
        off-y (js/Math.floor (/ y cell-size))
        px (* off-x cell-size)
        py (* off-y cell-size)
        dx (- (* (inc off-x) cell-size) w)
        dy (- (* (inc off-y) cell-size) h)]
    [[px py]
     [mod-x mod-y]
     [dx dy]
     [mod-w mod-h]]))

(defn grid-snap-coords [owner cell-size snap-threshold ev]
  (let [rel-x (om/get-state owner :rel-x)
        rel-y (om/get-state owner :rel-y)
        node (om/get-node owner)
        ev-x (.. ev -pageX)
        ev-y (.. ev -pageY)
        w (.-clientWidth node)
        h (.-clientHeight node)
        [[px py]
         [mod-x mod-y]
         [dx dy]
         [mod-w mod-h]] (nearest-corner cell-size
                                        (- ev-x rel-x)
                                        (- ev-y rel-y)
                                        w h)]
    [(cond
      (< mod-x snap-threshold) px
      (< mod-w snap-threshold) dx
      :else (- ev-x rel-x))
     (cond
      (< mod-y snap-threshold) py
      (< mod-h snap-threshold) dy
      :else (- ev-y rel-y))]))

(defn grid-snap-drag [owner cell-size snap-threshold]
  (reify
    IDragStart
    (drag-start [_ event]
      (-drag-start owner event))
    IDragMove
    (drag-move [_ event]
      (when (om/get-state owner :dragging)
        (let [[nx ny] (grid-snap-coords owner cell-size snap-threshold event)]
          (om/set-state! owner :hor-value nx)
          (om/set-state! owner :ver-value ny))))
    IDragEnd
    (drag-end [_ event]
      (when (om/get-state owner :dragging)
        (let [[nx ny] (grid-snap-coords owner cell-size snap-threshold event)]
          (om/set-state! owner :dragging false)
          (om/set-state! owner :hor-value nx)
          (om/set-state! owner :ver-value ny))))))

;;******************************************************************************
;;   - Guideline-snap-drag
;;******************************************************************************

(defn bbox->guideline [guideline]
  {:hor [(:top guideline) (/ (+ (:top guideline) (:height guideline)) 2) (+ (:top guideline) (:height guideline))]
   :ver [(:left guideline) (/ (+ (:left guideline) (:width guideline)) 2) (+ (:left guideline) (:width guideline))]})

(defn guidelines->stops [guidelines]
  {:hor (vec (sort (mapcat :hor (map bbox->guideline guidelines))))
   :ver (vec (sort (mapcat :ver (map bbox->guideline guidelines))))})

(defn closest-stop [series axis-position & [pos last-value last-diff]]
  "Simple method for walking a series until we find the position that
   minimizes the difference between axis-position and stop-position"
  (if (= (count series) pos)
    [last-value last-diff]
    (let [pos (or pos 0)
          cur-val (nth series pos)
          cur-diff (Math/abs (- axis-position cur-val))]
      (if (and last-value
               (> cur-diff last-diff))
        [last-value last-diff]
        (closest-stop series axis-position (inc pos) cur-val cur-diff)))))

(defn nearest-stops [guidelines position magnitude]
  [(closest-stop guidelines position)
   (closest-stop guidelines (+ position (/ magnitude 2)))
   (closest-stop guidelines (+ position magnitude))])

(defn guideline-snap-coords [owner guidelines snap-threshold ev]
  (let [rel-x (om/get-state owner :rel-x)
        rel-y (om/get-state owner :rel-y)
        node (om/get-node owner)
        ev-x (.. ev -pageX)
        ev-y (.. ev -pageY)
        w (.-clientWidth node)
        h (.-clientHeight node)
        stops (guidelines->stops guidelines)
        [[origin-cx origin-diff-cx]
         [center-cx center-diff-cx]
         [dest-cx dest-diff-cx]]  (nearest-stops (:ver stops)
                                                 (- ev-x rel-x)
                                                 w)
        [[origin-cy origin-diff-cy]
         [center-cy center-diff-cy]
         [dest-cy dest-diff-cy]] (nearest-stops (:hor stops)
                                                (- ev-y rel-y)
                                                h)]
    [(cond
      (< origin-diff-cx snap-threshold) [:left origin-cx]
      (< center-diff-cx snap-threshold) [:left (- center-cx (/ w 2))]
      (< dest-diff-cx snap-threshold)   [:left (- dest-cx w)]
      :else [:left (- ev-x rel-x)])
     (cond
      (< origin-diff-cy snap-threshold) [:top origin-cy]
      (< center-diff-cy snap-threshold) [:top (- center-cy (/ h 2))]
      (< dest-diff-cy snap-threshold)   [:top (- dest-cy h)]
      :else [:top (- ev-y rel-y)])]))

(defn guideline-snap-drag [owner snap-threshold]
  (reify
    IDragStart
    (drag-start [_ event]
      (-drag-start owner event))
    IDragMove
    (drag-move [_ event]
      (when (om/get-state owner :dragging)
        (let [[[hor-side cx]
               [ver-side cy]] (guideline-snap-coords owner (om/get-shared owner :guidelines) snap-threshold event)]
          (om/set-state! owner :hor-value cx)
          (om/set-state! owner :ver-value cy))))
    IDragEnd
    (drag-end [_ event]
      (when (om/get-state owner :dragging)
        (let [[[hor-side cx]
               [ver-side cy]] (guideline-snap-coords owner (om/get-shared owner :guidelines) snap-threshold event)]
          (om/set-state! owner :dragging false)
          (om/set-state! owner :hor-value cx)
          (om/set-state! owner :ver-value cy))))))

;;******************************************************************************
;;  Draggable Component
;;******************************************************************************

(defn draggable [data owner opts]
  (reify
    om/IWillMount
    (will-mount [_]
      (om/set-state! owner :hor-side :left)
      (om/set-state! owner :hor-value (or (:left data) 0))
      (om/set-state! owner :ver-side :top)
      (om/set-state! owner :ver-value (or (:top data) 0))
      (let [move (async/tap (get-in data [:comms :mouse-move :mult]) (chan))
            up   (async/tap (get-in data [:comms :mouse-up :mult]) (chan))
            dragger-fn (:dragger-fn opts)
            dragger (dragger-fn owner data)]
        (om/set-state! owner :dragger dragger)
        (go (while true
              (alt!
               move ([ev]
                       (drag-move dragger ev))
               up ([ev]
                     (drag-end dragger ev)))))))
    om/IRender
    (render [_]
      (let [dragger (om/get-state owner :dragger)]
        (dom/div #js {:className "draggable"
                      :style (clj->js {:left (om/get-state owner :hor-value)
                                       :top  (om/get-state owner :ver-value)
                                       :position "absolute"})
                      :onMouseDown (fn [ev]
                                     (.preventDefault ev)
                                     (drag-start dragger ev))
                      :onMouseUp #(drag-end dragger %)}
                 (om/build (:sub-com data) (:sub-com-data data) {:opts opts}))))))
