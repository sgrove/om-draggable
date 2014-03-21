# om-draggable

A draggable meta-component with a few different dragging behaviors: `free-drag`, `grid-snap-drag`, and `guideline-snap-drag`.

## Usage

    (ns visualize.core
      (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
                [draggable.core :as dnd]
                [om.core :as om :include-macros true]
                [om.dom :as dom :include-macros true])
      (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]))
    
    (om/root dnd/draggable
             {:top 10
              :left 10
              :sub-com block-com
              :sub-com-data {:color %1
                             :size %2}}
             {:opts {:dragger-fn (fn [owner _]
                                   (dnd/free-drag owner (:snap-threshold app)))}})

## LICENSE

All code released under the MIT license

## Copyright

BUSHIDO INC. 2014
