(ns dolores.time-window-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [dolores.time-window :as tw])
  (:import [java.time Instant Duration]))

(deftest compare-by-key-test
  (let [f (tw/compare-by-key :a)]
    (is (pos? (f {:a 2} {:a 1})))
    (is (neg? (f {:a 1} {:a 2})))
    (is (zero? (f {:a 1} {:a 1})))))

(deftest truncate-test
  (let [t1 (Instant/ofEpochMilli 2000)
        t2 (Instant/ofEpochMilli 1000)
        t3 (Instant/ofEpochMilli 500)
        items [{:id 1 :ts t1} {:id 2 :ts t2} {:id 3 :ts t3}]
        cutoff (Instant/ofEpochMilli 1500)
        result (tw/truncate items :ts cutoff)]
    (is (= [1] (map :id (vec result))))))

(deftest time-window-insert-collect-test
  (let [now (Instant/ofEpochMilli 1000)
        window-size (Duration/ofSeconds 1)
        tw0 (tw/time-window :ts window-size
                             :time-fn (constantly now))
        tw1 (tw/insert tw0 {:ts now})
        tw2 (tw/insert-items tw1 [{:ts now} {:ts now}])]
    (is (= [{:ts now}] (tw/collect tw1)))
    (is (= [{:ts now}] (tw/collect tw2)))))

(deftest time-window-assertion-test
  (testing "time-window requires Duration for window-size"
    (is (thrown? AssertionError
                 (tw/time-window :ts 5)))))

(deftest time-window-expiry-test
  (testing "TimeWindow removes items older than window-size"
    (let [fixed-now   (Instant/ofEpochMilli 2000)
          window-size (Duration/ofMillis 1000)
          tw0         (tw/time-window :ts window-size :time-fn (constantly fixed-now))
          item-old    {:ts (Instant/ofEpochMilli 500)}
          item-valid  {:ts (Instant/ofEpochMilli 1500)}
          tw1         (tw/insert tw0 item-old)
          tw2         (tw/insert tw1 item-valid)]
      (is (= [item-valid] (tw/collect tw2)))
      (is (= [item-valid] (tw/collect (tw/trim tw2)))))))

(run-tests)
