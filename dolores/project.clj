(defproject dolores "0.1.0-SNAPSHOT"
  :description "Dolores API Service"
  :dependencies [[org.clojure/clojure "1.10.3"]
                 ;; Add other dependencies here
                 ]
  :main ^:skip-aot dolores.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
