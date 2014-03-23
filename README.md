# om-draggable

A draggable meta-component with a few different dragging behaviors: `free-drag`, `grid-snap-drag`, and `guideline-snap-drag`.

## Demo

Run the visualize example to get a sense of the draggable behaviors:

    lein cljsbuild once
    lein simpleton 4005
    open http://localhost:4005/examples/visualize/


## Usage

```clj
(ns visualize.core
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [draggable.core :as dnd]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(def app {:top 10
          :left 10
          :sub-com block-com
          :sub-com-data {:color %1
                         :size %2}
          :snap-threshold 25})

(om/root dnd/draggable
         app
         {:opts {:dragger-fn (fn [owner _]
                               (dnd/free-drag owner (:snap-threshold app)))}})
```

## LICENSE

All code released under the MIT license

## Copyright

BUSHIDO INC. 2014
