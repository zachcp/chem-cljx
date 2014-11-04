(ns chem-cljx.core
  "core file for chem-cljx"
  (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Schemas

(def Atom
  "Standard Atom Representation"
  {:atoms s/Str
   :connections [s/Num]})

(def Bond
  "Standard Bond Representation"
  {:atoms s/Str
   :connections [s/Num]})

(def Molecule
  "Standard Atom Representation"
  {:atoms [Atom]
   :connections [Bond]})

