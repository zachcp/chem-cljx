(ns chem-cljx.smilesparser
  "Namsespace for Parsing Smiles using Instaparse for the heavy lifting"
  (:require [clojure.edn :as edn]
            [clojure.string :as string]
            [instaparse.core :as insta]
            [plumbing.graph :as graph] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Schemas
(def Atomlist
  "intermediate representation of parsed smile"
  [])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Support Functions

(defn- in? [seq elm ]
  (some #(= elm %) seq))

(defn- getsinglebonds [atomlist]
  "returns single bonds from adjacent atoms"
  (let [bonds (for [i (range (dec (count atomlist)))]
                (cond (and (map? (nth atomlist i))
                           (map? (nth atomlist (inc i))))
                      {:order :Single
                       :aromatic :No
                       :type  :Dot
                       :atoms [ i (inc i)]}))]
    (remove nil? bonds)))

(defn- getbondindices [coll]
  "return the indices of the collection that are explicit bonds"
  (map first
       (filter #(contains? #{:Single :Double :Triple} (second %))
               (map-indexed vector coll))))

(defn- getbranchedatomindex [idx coll open close]
  "find the atom to which a bond belongs if the bond is separated by brackets"
  (cond
    (< idx 0)
      "There is an error. This should not be zero"
    ; where bond has to skip over a single open bracket
    ; example: C(=O)
    (and (= open 1) (= close 0) (map? (nth coll idx)))
      idx
    ; where bond has to skipover one or more entire branced ;
    ; example C(CC)=O
    ; example C(CCCC(=O)CC)=O
    (and (= open close) (> close 0) (map? (nth coll idx)))
    idx
    ; the next three just increment down while counting the backet types
    (= :OpenBranch (nth coll idx))
      (getbranchedatomindex (dec idx) coll (inc open) close)
    (= :CloseBranch (nth coll idx))
      (getbranchedatomindex (dec idx) coll open (inc close))
    :else (getbranchedatomindex (dec idx) coll open close)))

(defn- getexplicitbonds [atomlist]
  "returns double bonds from adjacent atoms
   must take into account:
        direct:   C=C
        indirect: C(=O)
        nested:   C(C)(=O)"
  (let [dblocs   (getbondindices atomlist)
        checkmap (fn [x] (map? (nth atomlist x)))
        direct?  (fn [x] (if (and (checkmap (inc x)) (checkmap (dec x)))
                           true false ))
        indirect? (fn [x] (if (and (checkmap (inc x)) (checkmap (dec (dec x))))
                            true false))]
    (for [d dblocs]
      (do
        (cond
          (direct? d)   {:order (nth atomlist d) :atoms [(dec d) (inc d)] }
          (indirect? d) {:order (nth atomlist d) :atoms [ (dec (dec d)) (inc d)]}
          :else         {:order (nth atomlist d) :atoms [ (getbranchedatomindex (dec d) atomlist 0 0)  (inc d)]}  )))))

(defn- getbrokenringbonds [atomlist]
  "Return Bonds from Broken rings and Single Bonds
   For now just return the atom immedietely to the left of the integer.
  ToDO: add use cases if there are multiple integers associated with the same atom"
  (let [mapindexed (into [] (map-indexed vector atomlist))
        ringlocs   (filter #(integer? (second %)) mapindexed)
        matches    (for [x ringlocs y ringlocs
                         :when (= (second x) (second y) )
                         :while (not= (first x) (first y))]
                     [x y]) ]
    (for [[a b] matches]
      (let [mi (min (first a) (first b))
            ma (max (first a) (first b))]
        {:order :Single
         :aromatic :No
         :type  :Dot
         :atoms [ (dec mi) (dec ma)]}))))

(defn- getadjacentringbonds [atomlist]
  "ring integers break up the detection of single rings find ring locations and add the bonds"
  (let [mapindexed (into [] (map-indexed vector atomlist))
        isint?     (fn [x] (if (number? (read-string (str x))) true false))
        ringlocs   (filter #(integer? (second %)) mapindexed)]
    ;     ringlocs))
    (for [ [i n] ringlocs]
      (cond
        (>= i (- (count atomlist) 1))
        nil
        (and (map? (nth atomlist (inc i))) (map? (nth atomlist (dec i))))
        {:order :Single
         :aromatic :No
         :type  :Dot
         :atoms [ (dec i) (inc i )]}
        :else "Problem!"  ))))

(defn- getmapping [atomlist]
  "create a mapping for atom positions once the bond and branchings are removed"
  (let [mapindexed (into [] (map-indexed vector atomlist))
        filteratoms (filter #(map? (second %)) mapindexed)
        atomindexes  (map first filteratoms)]
    (zipmap atomindexes (range))))

(defn- updatebond [bond mapping]
  "update the bond with new atomnumbers"
  (let [newvals (into [] (map mapping (:atoms bond)))]
    (assoc bond :atoms newvals)))

(defn- getbonds [atomlist]
  "Bonds From Atom List"
  (let [singlebonds (getsinglebonds atomlist)
        doublebonds (getexplicitbonds atomlist)
        ringbonds   (getbrokenringbonds atomlist)
        ringbonds2  (getadjacentringbonds atomlist)
        allbonds    (concat singlebonds doublebonds ringbonds ringbonds2)]
    (into [] (map #(updatebond % (getmapping atomlist)) allbonds ))))


(defn- make-atom
  "generic function to create an atom. default is a carbon atom"
  [{:keys   [element isotope charge hydrogens atomclass chiral aromatic]
    :or {element "C",     isotope nil
         charge  0,      hydrogens 1
         atomclass nil,  chiral nil }} ]
  (hash-map :element   element,   :isotope isotope,
            :charge    charge,    :hydrogens hydrogens,
            :atomclass atomclass, :chiral chiral
            :aromatic  :false))

(defn- make-bond
  "generic function to create an bond. default is a singlebond"
  [{:keys   [order aromatic btype atom1 atom2]
    :or {order :Single, aromatic :false btype :Dot}} ]
  (hash-map  :order :Single
             :aromatic :No
             :type  :Dot
             :atoms [ atom1 atom2]))

(def smilesparser
  "EBNF Parsing grammar for Smiles based off of the following
   http://metamolecular.com/cheminformatics/smiles/formal-grammar/
   http://frowns.cvs.sourceforge.net/frowns/frowns/smiles_parsers/Smiles.py?view=markup
   http://miningdrugs.blogspot.com/2007/01/molecular-query-languages-flexmol-mql.html
   http://baoilleach.blogspot.com/2007/06/parsing-smiles-with-frown.html
  "
  (insta/parser
    "<SMILES>   = (Atom | Bond | OpenBranch |CloseBranch | Ring)*
     OpenBranch   = <'('>
     CloseBranch  = <')'>
     Atom     = OrganicSymbol | AromaticSymbol | AtomSpec | WILDCARD
     Bond     = #'[\\|-|=|#|$|:|/|.]'

     AtomSpec = <'['> Isotope? ( 'se' | 'as' | AromaticSymbol | ElementSymbol | WILDCARD ) ChiralClass? HCount? Charge? Class? <']'>
     OrganicSymbol = 'B' ['r'] |'C' ['l'] | 'N' | 'O' | 'P' | 'S' | 'F' | 'I'
     AromaticSymbol = 'b' | 'c' | 'n' | 'o' | 'p' | 's'
     WILDCARD = '*'
     ElementSymbol = letter_uc letter_lc?
     Ring = number
     ChiralClass = ( '@' ('@' |
                          'TH' #'[1-2]' |
                          'AL' #'[1-2]' |
                          'SP' #'[1-3]' |
                          'TB' ( '1' #'[0-9]'? |'2' '0'? | #'[3-9]' ) |
                          'OH' ( '1' #'[0-9]'? | '2' #'[0-9]'? | '3' '0'? | #'[4-9]' ))?)?
     Charge   = '-' ( '-' | '0' | '1' #'[0-5]'? | #'[2-9]' )?
              | '+' ( '+' | '0' | '1' #'[0-5]'? | #'[2-9]' )?
     HCount   = (<'H'> number)
     Class    = (<':'> number)
     Isotope  = number

     <letter_lc> = #'[a-z]'
     <letter_uc> = #'[A-Z]'
     <number> = #'[0-9]+' "))

(def smilesparsertransformations
  "a list of transformations to apply to the instaparse tree.
   the transormations are ordered from lowest on the tree to highest.
   the lowest ones are mostly converting to strings and so on
   the higher ones will create atoms/bonds

   notice that the keywords are changed from the grammar format to the make-atom format
  "
  {;lowest level transformations
   :ElementSymbol (fn [& x] [:element   (apply str x)])
   :ChiralClass   (fn [& x] [:chiral    (apply str x )])
   :Isotope       (fn [x]   [:isotope   (edn/read-string (str x ))])
   :HCount        (fn [& x] [:hydrogens (first (edn/read-string (str x )))])
   :Charge        (fn [& x] [:charge    (first (edn/read-string (str x )))])
   :Class         (fn [& x] [:class     (apply str x)] )
   :Ring          (fn [x]   [:Ring      (edn/read-string (str x ))])
   :Bond          (fn [x]   (let [bondtypes {"-" :Single "=" :Double "#" :Triple}]
                              (get bondtypes x)))
   ;Implement these bond types \ $ : / .
   :OpenBranch    (fn [] :OpenBranch)
   :CloseBranch   (fn [] :CloseBranch)
   ;mapping atom types
   :Atom          (fn [x]   (condp = (first x)
                              ;:OrganicSymbol  (make-atom {:element (second x)})
                              ;:AromaticSymbol (make-atom {:element (second x) :aromatic :true})
                              ;:AtomSpec       (make-atom (into {}  (rest x)))
                              :OrganicSymbol  {:element (apply str (rest x))}
                              :AromaticSymbol {:element (second x) :aromatic :true}
                              :AtomSpec       (into {}  (rest x))
                              :WildCard       "wildydildy"))})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public


;put output of parser in order to get atoms and then bonds
(defn parse-smiles [smi]
  "Function that will get atoms and bonds from the smiles parser"
  (let [par (smilesparser smi)
        trans (insta/transform smilesparsertransformations par)
        atoms (into [] (filter map? trans))
        bonds (getbonds trans) ]

    {:atoms atoms
     :bonds bonds}))

(comment
  "you need to get the bond stuff together.
   Ring finding will only work after the fact.
   right now there is the issue of getting bond info for non-adjacent atoms
   Currently issue in the get explicit bonds function"

  (def parse-smiles
    "parse-smiles does a series of computations to turn a smiles string into a
     a chemical structure graph. It uses the instaparse grammar to
      1. parse a smiles string into instaparse format
      2. transform this format into a hiccup vector with atoms in brackets accoutned for
      3. obtain all of the bond information
      4. combine this information into the final format."
    {:parsed  (graph/fnk [smi]    (insta/transform smilesparsertransformation (smilesparser smi)))
     :sbonds  (graph/fnk [parsed] (getsinglebonds parsed))
     :bsbonds (graph/fnk [parsed] (getbrokenringbonds parsed))
     :dbonds  (graph/fnk [parsed] (getexplicit parsed))
     :arbonds (graph/fnk [parse]  (getadjacentringbonds parsed))
     })



  (def smilestest
    (insta/transform
      smilesparsertransformations
      (smilesparser "C1=Cl=C1(cccc)c=c[13Te@TH1]")))

  (def smilestest2
    (insta/transform
      smilesparsertransformations
      (smilesparser "CC(=O)Oc1ccccc1C(=O)[O-]")))

  smilestest2
  (getsinglebonds smilestest)
  smilestest2

  (getbranchedatomindex 3 smilestest2 0 0)
  (getexplicitbonds smilestest2)
  (getbondindices smilestest)
  smilestest

  )