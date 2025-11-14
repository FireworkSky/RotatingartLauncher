// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package net.dot.android.crypto;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;

public final class DotnetProxyTrustManager implements X509TrustManager {
   private final long sslStreamProxyHandle;

   public DotnetProxyTrustManager(long var1) {
      this.sslStreamProxyHandle = var1;
   }

   public void checkClientTrusted(X509Certificate[] var1, String var2) throws CertificateException {
      if (!verifyRemoteCertificate(this.sslStreamProxyHandle)) {
         throw new CertificateException();
      }
   }

   public void checkServerTrusted(X509Certificate[] var1, String var2) throws CertificateException {
      if (!verifyRemoteCertificate(this.sslStreamProxyHandle)) {
         throw new CertificateException();
      }
   }

   public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
   }

   static native boolean verifyRemoteCertificate(long var0);
}
