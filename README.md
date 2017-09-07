# Java HTTPS Tester

A small program to help you debug HTTPS connection problems with
JVM-based programs. 

# Motivation

In
[Java 1.8.0_141](http://www.oracle.com/technetwork/java/javase/8u141-relnotes-3720385.html) Oracle
removed support for SHA-1 signed certificates in HTTPS
connections. We've seen connection problems after such a Java update
in production systems when not all certificates where updated before
the Java update was rolled out.

This program can be used to test and analyze an HTTPS connection
established by a JVM-based program. You can test your local Java
installation and your server-settings with this.


# Build

You need to install [Leiningen](https://leiningen.org/). Don't worry,
it's really easy. Just a bash script in your path on Linux, OSX or
Cygwin. There is also a `.bat`` file for Windows.

To create the executable JAR, just run

    shell> lein uberjar
    
in the cloned repository. The resulting stand-alone-JAR will be in the
`target` folder.

# Usage

You can simply run the executable JAR with an URL to connect to (use
the correct name for the JAR):

    java -jar https-tester.jar https://www.example.com
    
This program will connect to the server at least two times:

* Once using the Apache HTTP Commons library, 
* and once using the built-in classes of the JRE.

It may output some information on STDOUT, but the main result is a new
logfile in the current directory. The logfile's contents may be a lot
to digest, but look at it line by line and you'll see that it is
pretty readable.

We are not using the Apache library directly, but via the Clojure
library [clj-http](https://github.com/dakrone/clj-http). The `get`
function of that library allows some configurations.  We already pass
in

    {:throw-exceptions true
     :debug true
     :response-interceptor interceptor}

which can not be overwritten.  The interceptor should output some logs
when you follow redirects.  For other things like proxy
configurations, keystores, or basic auth, see the documentation
at [clj-http](https://github.com/dakrone/clj-http). 

Pass the configuration map as an [EDN]() formatted string parameter:

    java -jar https-tester.jar -c '{:proxy-host "127.0.0.1"  :proxy-port 8118}' URL...


When connecting to the URL the second time using the common Java
classes, you can control its behavior by setting the appropriate
system properties.

For example, you could create extensive SSL debug logging by calling
the program like this:

    java -Djavax.net.debug=ssl -jar https-tester.jar https://www.example.com

Note, that the extra output is not caught in the logfile but goes to
STDOUT. See
http://docs.oracle.com/javase/7/docs/technotes/guides/security/jsse/ReadDebug.html
and
https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#InstallationAndCustomization
for more.

If the connection could not be established so far, often because of
SSL handshake errors, the program tries a third time using an all
trusting trust manager.  If this succeeds, you'll find the certificate
chain information in the log file, too.

# Hints

Some hints what you want to look for in your Java installation. We've
seen several reasons for programs not being able to establish a
connection. Your mileage may vary.

* The most thorough document for everything related to this topic is
  probably the
  [JSSE Reference Guide](http://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html).
* Find the file `java.security` in your `JAVA_HOME/jre/lib/security`
  folder. There is a setting called `jdk.certpath.disabledAlgorithms`
  which was changed significantly in Java 1.8.0_141. It restricts
  usage of SHA-1-signed certificates for TLS connections. Compare it
  to older versions and adjust if you absolutely have to. But be
  warned that you are reducing the security by doing so. Always prefer
  using better certificates on the server.
* In some cases the `cacerts` file in that same folder caused problems
  for us.
* Depending on the configuration of the server you are connecting to,
  you may have to install
  the
  [Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy](http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html). Otherwise
  some ciphers will not be available.
* If you are installing Oracle Java 8 on a developer Linux machine
  using the [webupd8 PPA](https://launchpad.net/~webupd8team), you may
  find out that you did not receive the changes on your last
  update. The installer manages some of the files in the `security`
  folder via symlinks to `/etc`.  Analyze those carefully.
* Another, much more elaborate, tool is the `s_client` module from the
  OpenSSL project. It can validate and output certificates and help a
  lot with debugging connection problems. It does not use the JVM
  though, and is thus a different beast. You often use it like this:
  
    openssl s_client -showcerts -connect www.example.com:443

# License

This program is Copyright (c) 2017 by Acrolinx GmbH and published
under the Apache License v2.0. See the LICENSE file for details.
