(ns chem-cljx.ringfinder
  "Namsespace for Finding Rings in Molecules"
  (:require [clojure.edn :as edn]
            [clojure.string :as string]
            [instaparse.core :as insta]
            [plumbing.graph :as graph] ))

;; check out the mcb of CDK
;; http://www.ncbi.nlm.nih.gov/pmc/articles/PMC3922685/#!po=59.3750
;; aim to implement the Figueras ring perception algorithm
;; http://pubs.acs.org/doi/full/10.1021/ci960013p

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Schemas

(def AtomProperties
  "Table Used For Calculating Cycles"
  {:atom s/Str
   :connections [s/Num]})

(def AdjacencyTable
  "Adjacency Table for Calcualting Cycles"
  [AtomProperties])

