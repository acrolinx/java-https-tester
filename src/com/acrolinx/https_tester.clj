;; (c) 2017 Acrolinx GmbH
;; Published under the ASL 2.0
(ns com.acrolinx.https-tester
  (:require
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [clojure.tools.cli :as opts]
   [clojure.edn :as edn])
  (:import
   org.apache.log4j.Logger
   [java.net
    MalformedURLException
    URL
    HttpURLConnection
    URLDecoder]
   java.security.cert.Certificate
   [javax.net.ssl
    HttpsURLConnection
    HostnameVerifier
    SSLServerSocketFactory
    TrustManager
    SSLContext
    X509TrustManager])
  (:gen-class))

(def ^:private FILEWATCH-DELAY-MS 30000)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Logging Utils
(defn log-headers [resp]
  (log/info "==== Response Headers ==== ")
  (doseq [[k v] (:headers resp)]
    (log/infof "   %s:\"%s\"" k v)))

(defn log-sysprops []
  (log/info "==== System Properties ==== ")
  (doseq [[k v] (System/getProperties)]
    (log/infof "   %s:\"%s\"" k v)))

(defn log-ciphers []
  (log/info "==== Local Cipher Suites ==== ")
  (let [sssf (SSLServerSocketFactory/getDefault)
        default-cs (.getDefaultCipherSuites sssf)
        is-default? (set default-cs)
        support-cs (.getSupportedCipherSuites sssf)
        ]
    (log/info "Enabled cipher suites prefixed with a ✓")
    (doseq [cs support-cs]
      (log/infof "%s %s"
                (if (is-default? cs) "✓" " ")
                cs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Java Net Backend
(defn x509->pem [cert]
  (str
   "-----BEGIN CERTIFICATE-----\n"
   (-> cert
       .getEncoded
       javax.xml.bind.DatatypeConverter/printBase64Binary
       (.replaceAll "(.{64})" "$1\n"))
   "\n-----END CERTIFICATE-----"))
  
(defn log-certs [certs]
  (log/info "== Certificates ==")
  (doseq [[chain-n cert] (map-indexed vector certs)]
    (log/info "Item number in certificate chain:" chain-n)
    (log/infof "Certificate type[%s]: %s" chain-n (.getType cert))
    (when (instance? java.security.cert.X509Certificate cert)
      ;; this does not work as expected??
      ;;(log/info "  valid:" (.checkValidity cert))
      (log/infof "Certificate as PEM[%s]:\n%s\n" chain-n (x509->pem cert))
      (log/infof "Full details[%s]:\n<<<<<\n%s\n>>>>>"
                 chain-n cert))))

(defn log-connection-info [conn]
  (let [cipher (.getCipherSuite conn)
        certs  (.getServerCertificates conn)]
    (log/info "== Connection Info ==")
    (log/infof "Cipher suite: %s" cipher)
    (log-certs certs)))

(defn maybe-add-all-trusting [conn trust-all?]
  (if (not trust-all?)
    conn
    (let [tm (make-array X509TrustManager 1)
          ;; going low on SSL here to allow more connections.
          ssl-ctx (SSLContext/getInstance "SSL")]
      (log/info "Adding an all trusting trust manager")
      (aset tm 0
            (proxy [X509TrustManager][]
              (getAcceptedIssuers [])
              (checkClientTrusted [_ _])
              (checkServerTrusted [_ _])))
      (.init ssl-ctx nil tm (java.security.SecureRandom.))
      (.setSSLSocketFactory conn (.getSocketFactory ssl-ctx))
      (.setHostnameVerifier conn
                            (reify HostnameVerifier
                              (verify [this _ _] true)))
      conn)))

  
(defn url-connection
  ([j-n-url]
   (url-connection j-n-url false))
  
  ([j-n-url trust-all?]
   (doto (.openConnection j-n-url)
     (.setReadTimeout 5000)
     (.setConnectTimeout 5000)
     (.setInstanceFollowRedirects false)
     (.setRequestProperty "User-Agent" "HTTPS Test Client")
     (maybe-add-all-trusting trust-all?)
     .connect)))

(defn connect* [j-n-url url trust-all?]
  (log/info "Connecting to" url)
  (log/info "trusting all certificates?" trust-all?)
  (try
    (if-let [conn (url-connection j-n-url trust-all?)]
      (let [ret (.getResponseCode conn)]
        ;; handle redirects
        (cond
          (or (= ret HttpURLConnection/HTTP_MOVED_PERM)
              (= ret HttpURLConnection/HTTP_MOVED_TEMP))
          (let [new-loc (-> conn
                            (.getHeaderField "Location")
                            (URLDecoder/decode "UTF-8"))
                ;; two arg form for relative URLs
                new-url (URL. j-n-url new-loc)]
            (log/info "Following redirect to" new-loc)
            (connect* new-url new-loc trust-all?))
          ;; got a response other than redirect
          :else
          (log-connection-info conn))))
    (catch Exception e
      (log/infof "=== ERROR ===")
      (log/info (.getMessage e))
      (log/info e)
      (when-not trust-all?
        (log/info "Trying again with an all trusting trust manager")
        (try
          (connect* j-n-url url true)
          (catch Exception e
            (log/info "Still getting exceptions.")
            (log/info " Could be you have to install JCE")))))))

(defn connect-java [url]
  (log/info "==== Connect Backend Java Net SSL ====")
  (try
    (connect* (URL. url) url false)
    (catch MalformedURLException mue
      (log/warnf "Bad URL: %s" url))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Apache Commons Backend
(defn interceptor [resp ctx]
  (log/info "=== Interceptor ===")
  (log/info resp)
  (log/info ctx))

(defn connect-commons [url config]
  (log/info "==== Connect Backend Apache HTTP ====")
  (log/info "Connecting to" url)
  (try
    (let [confmap (merge config
                         {:throw-exceptions true
                          :debug true
                          :response-interceptor interceptor})
          res (client/get url confmap)]
      (log/info "=== SUCCESS ===")
      (log-headers res)
      (log/info "=== Body ===")
      (log/info "BEGIN-OF-BODY>>>>>")
      (log/info (:body res))
      (log/info "<<<<<END-OF-BODY"))
    (catch Exception e
      (log/info "=== ERROR ===")
      (log/info (.getMessage e))
      (log/info e))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; General

(def cli-options
  [["-h" "--help" "Print usage and exit."]
   ["-c" "--config EDN-STRING"
    "A string with options for clj-http."
    :default nil]])

(defn print-usage-and-exit [summary]
  (println "Options:\n" summary)
  (println "
This program helps debugging SSL connection problems in Java land.

This is just a short usage hint. Refer to the README of this project
for more information and examples how to run it.

Run it as 

 java -jar this-program.jar http://your-url

The result will be a new log file with all information we could
gather. That may be hard to read, but take your time and you'll figure
out.")
  (System/exit 0))

(defn commons-configuration [opt]
  (if opt
    (edn/read-string opt)
    {}))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]}
        (opts/parse-opts args cli-options)]
    (when errors
      (println "Error parsing command line options:\n")
      (println (clojure.string/join \newline errors))
      (System/exit 1))
    (when (:help options)
      (print-usage-and-exit summary))

    (println "Logging information to"
             (-> (Logger/getRootLogger)
                 .getAllAppenders
                 enumeration-seq
                 first
                 .getFile))
    (log/with-logs *ns*

      (log/info "===== START =====")

      (log-sysprops)
      (log-ciphers)
      (log/info "Analyzing: " (first arguments))
      (connect-commons (first arguments)
                       (commons-configuration (:config options)))
      (connect-java (first arguments))
      (log/info "===== END ====="))))
