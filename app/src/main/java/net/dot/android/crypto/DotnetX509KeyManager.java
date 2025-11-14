// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package net.dot.android.crypto;

import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import javax.net.ssl.X509KeyManager;

public final class DotnetX509KeyManager implements X509KeyManager {
   private static final String CLIENT_CERTIFICATE_ALIAS = "DOTNET_SSLStream_ClientCertificateContext";
   private final PrivateKey privateKey;
   private final X509Certificate[] certificateChain;

   public DotnetX509KeyManager(KeyStore.PrivateKeyEntry var1) {
      if (var1 == null) {
         throw new IllegalArgumentException("PrivateKeyEntry must not be null");
      } else {
         this.privateKey = var1.getPrivateKey();
         Certificate[] var2 = var1.getCertificateChain();
         ArrayList var3 = new ArrayList();
         Certificate[] var4 = var2;
         int var5 = var2.length;

         for(int var6 = 0; var6 < var5; ++var6) {
            Certificate var7 = var4[var6];
            if (var7 instanceof X509Certificate) {
               var3.add((X509Certificate)var7);
            }
         }

         if (var3.size() == 0) {
            throw new IllegalArgumentException("No valid X509 certificates found in the chain");
         } else {
            this.certificateChain = (X509Certificate[])var3.toArray(new X509Certificate[0]);
         }
      }
   }

   public String[] getClientAliases(String var1, Principal[] var2) {
      return new String[]{"DOTNET_SSLStream_ClientCertificateContext"};
   }

   public String chooseClientAlias(String[] var1, Principal[] var2, Socket var3) {
      return "DOTNET_SSLStream_ClientCertificateContext";
   }

   public String[] getServerAliases(String var1, Principal[] var2) {
      return new String[0];
   }

   public String chooseServerAlias(String var1, Principal[] var2, Socket var3) {
      return null;
   }

   public X509Certificate[] getCertificateChain(String var1) {
      return this.certificateChain;
   }

   public PrivateKey getPrivateKey(String var1) {
      return this.privateKey;
   }
}
