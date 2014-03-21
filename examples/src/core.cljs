(ns visualize.core
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(enable-console-print!)

;; Overcome some of the browser limitations around DnD
(def mouse-move-ch
  (chan (sliding-buffer 1)))

(def mouse-down-ch
  (chan (sliding-buffer 1)))

(def mouse-up-ch
  (chan (sliding-buffer 1)))

(js/window.addEventListener "mousedown" #(put! mouse-down-ch %))
(js/window.addEventListener "mouseup"   #(put! mouse-up-ch   %))
(js/window.addEventListener "mousemove" #(put! mouse-move-ch %))


;;******************************************************************************
;;  Settings and initial state
;;******************************************************************************

(def app-state (atom {:grid {:rows 20
                             :columns 20
                             :cell-size 250}
                      :snap-threshold 25
                      :colors (take 40 (cycle ["green" "blue" "purple" "red" "yellow" "black"]))
                      :block-sizes (repeatedly 40 #(+ 25 (rand-int 75)))
                      :comms {:mouse-move {:ch  mouse-move-ch
                                           :mult (async/mult mouse-move-ch)}
                              :mouse-up   {:ch  mouse-up-ch
                                           :mult (async/mult mouse-up-ch)}
                              :mouse-down {:ch  mouse-down-ch
                                           :mult (async/mult mouse-down-ch)}}}))

;;******************************************************************************
;;  Helper components for visualization
;;******************************************************************************

(defn grid [data owner opts]
  (reify
    om/IRender
    (render [_]
      (apply dom/div #js {:className "grid"}
             (doall (mapcat
                     #(do
                        (map (fn [x]
                               (dom/div #js {:style
                                             #js {:border-left-width   1
                                                  :border-top-width    0
                                                  :border-right-width  0
                                                  :border-bottom-width 1
                                                  :border-style "solid"
                                                  :border-color "#ccc"
                                                  :position "fixed"
                                                  :left x
                                                  :top %
                                                  :height (:cell-size data)
                                                  :width (:cell-size data)}}))
                             (range 0 (* (:columns data) (:cell-size data)) (:cell-size data))))
                     (range 0 (* (:rows data) (:cell-size data)) (:cell-size data))))))))

(defn block [data owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:style #js {:width  (:size data)
                                :height (:size data)
                                :background-color (:color data)
                                :borderRadius 200
                                :opacity 0.5}}
               (:content data)))))

(defn make-guideline [& [x y ow oh]]
  (let [longer (+ 300 (rand-int 600))
        shorter 2
        left (rand-int 600)
        top (rand-int 600)
        [w h] (if (pos? (rand-int 2))
                [longer shorter]
                [shorter longer])]
    {:width (or ow w)
     :height (or oh h)
     :top (or y top)
     :left (or x left)}))

(def guidelines
  (repeatedly 10 make-guideline)
  ;;[(make-guideline 50 50 2 500)]
  )

(defn guideline [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [guideline (:guideline data)]
        (dom/div #js {:style #js {:width  (:width guideline)
                                  :height (:height guideline)
                                  :top    (:top guideline)
                                  :left   (:left guideline)
                                  :position "absolute"
                                  :background-color "black"
                                  :opacity 0.8}})))))

;;******************************************************************************
;;  Main entry-point
;;******************************************************************************

(defn drag-app [app owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
               (om/build grid (:grid app) opts)
               (apply dom/div #js {:className "guidelines"}
                      (map #(om/build guideline (assoc app :guideline %)) guidelines))
               (apply dom/div #js {:id "free-drag"}
                      (map
                       #(om/build draggable (assoc app
                                              :top (+ (rand-int 20) 50)
                                              :left (+ (rand-int 20) 70)
                                              :color %
                                              :sub-com block
                                              :sub-com-data {:color %1
                                                             :size %2})
                                  {:opts {:dragger-fn (fn [owner] (free-drag owner))}})
                       (:colors app)
                       (:block-sizes app)))
               (apply dom/div #js {:id "grid-snap-drag"}
                      (map
                       #(om/build draggable (assoc app
                                              :top (+ (rand-int 20) 50)
                                              :left (+ (rand-int 20) 700)
                                              :color %
                                              :sub-com block
                                              :sub-com-data {:color %1
                                                             :size %2})
                                  {:opts {:dragger-fn (fn [owner _]
                                                        (grid-snap-drag owner
                                                                        (get-in app [:grid :cell-size])
                                                                        (:snap-threshold app)))}})
                       (:colors app)
                       (:block-sizes app)))
               (apply dom/div #js {:id "grid-snap-container"}
                      (map
                       #(om/build draggable (assoc app
                                              :top (+ (rand-int 20) 500)
                                              :left (+ (rand-int 20) 700)
                                              :color %
                                              :sub-com block
                                              :sub-com-data {:color %1
                                                             :size %2})
                                  {:opts {:dragger-fn (fn [owner _]
                                                        (guideline-snap-drag owner (:snap-threshold app)))}})
                       (:colors app)
                       (:block-sizes app)))))))

(om/root
 drag-app
 app-state
 {:target (. js/document (getElementById "app"))
  :shared {:guidelines guidelines}})
