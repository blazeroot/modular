;; Copyright © 2014, JUXT LTD. All Rights Reserved.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns modular.bidi
  (:require
   [modular.core :as mod]
   [schema.core :as s]
   [modular.ring :refer (RingHandlerProvider)]
   [com.stuartsierra.component :as component]
   [bidi.bidi :as bidi]))

(defn deref-if-possible [x]
  (if (instance? clojure.lang.IDeref x)
    (deref x)
    x))

;; If necessary, routes and context can return deref'ables if necessary,
;; for example, if their values are not known until the component is
;; started.
(defprotocol RoutesContributor
  (routes [_])
  (context [_]))

(defrecord BidiRoutes [routes context]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  RoutesContributor
  (routes [this] routes)
  (context [this] context))

(defn new-bidi-routes [routes & {:as opts}]
  (let [{:keys [context]} (->> (merge {:context ""} opts)
                               (s/validate {:context s/Str}))]
    (new BidiRoutes (delay routes) context)))

(defn wrap-routes
  "Add the final set of routes from which the Ring handler is built."
  [h routes]
  (fn [req]
    (h (assoc req ::routes routes))))

(defrecord BidiRingHandlerProvider []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  RingHandlerProvider
  (handler [this]
    (let [routes ["" (mapv #(vector (or (deref-if-possible (modular.bidi/context %)) "")
                                    [(deref-if-possible (routes %))])
                           (:routes-contributors this))]]
      (-> routes
          bidi/make-handler
          (wrap-routes routes)))))

;; Keep this around for integration with Prismatic Schema
(defn new-bidi-ring-handler-provider []
  (component/using
   (new BidiRingHandlerProvider)
   [:routes-contributors]))

(defn resolve-routes-contributors [system-map]
  (mod/resolve-contributors system-map :routes-contributors RoutesContributor))
