;; (c) 2017 Acrolinx GmbH
;; Published under the ASL 2.0
(defproject com.acrolinx.https-tester "0.1.0-SNAPSHOT"
  :description "A small program to help debugging HTTPS problems."
  :url "https://github.com/acrolinx/java-https-tester"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.4.0"]
                 [clj-http "3.6.1"]                 
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]]]
  :uberjar-name "java-https-tester.jar"
  :main com.acrolinx.https-tester
  :aot :all)
