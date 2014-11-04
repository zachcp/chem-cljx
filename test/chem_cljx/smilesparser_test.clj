(ns chem-cljx.smilesparser-test
  (:require [clojure.test :refer :all]
            [chem-cljx.smilesparser :refer :all]))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))

(deftest smilesparsertest
  (testing "Basic Smiles Parsing"
    (is (parse-smiles "CC" {:atoms [{:element "C"} {:element "C"}],
                            :bonds [{:order :Single, :aromatic :No, :type :Dot, :atoms [0 1]}]}))))