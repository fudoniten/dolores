(ns dolores.core-test
  (:require [clojure.test :refer :all]
            [dolores.core :refer :all]))

(deftest test-main
  (testing "Main function"
    (is (not (nil? (-main))))))
