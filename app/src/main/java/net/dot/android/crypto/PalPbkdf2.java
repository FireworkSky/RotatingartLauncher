// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package net.dot.android.crypto;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

public final class PalPbkdf2 {
    private static final int ERROR_UNSUPPORTED_ALGORITHM = -1;
    private static final int SUCCESS = 1;

    public PalPbkdf2() {
    }

    public static int pbkdf2OneShot(String var0, byte[] var1, ByteBuffer var2, int var3, ByteBuffer var4) throws ShortBufferException, InvalidKeyException, IllegalArgumentException {
        if (var0 != null && var1 != null && var4 != null) {
            String var5 = "Hmac" + var0;

            Mac var6;
            try {
                var6 = Mac.getInstance(var5);
            } catch (NoSuchAlgorithmException var15) {
                return -1;
            }

            if (var1.length == 0) {
                var1 = new byte[]{0};
            }

            if (var2 != null) {
                var2.mark();
            }

            SecretKeySpec var7 = new SecretKeySpec(var1, var5);
            var6.init(var7);
            int var8 = 1;
            int var9 = 0;
            byte[] var10 = new byte[4];
            byte[] var11 = new byte[var6.getMacLength()];

            for(byte[] var12 = new byte[var11.length]; var9 < var4.capacity(); ++var8) {
                writeBigEndianInt(var8, var10);
                if (var2 != null) {
                    var6.update(var2);
                    var2.reset();
                }

                var6.update(var10);
                var6.doFinal(var12, 0);
                System.arraycopy(var12, 0, var11, 0, var11.length);

                for(int var13 = 2; var13 <= var3; ++var13) {
                    var6.update(var12);
                    var6.doFinal(var12, 0);

                    for(int var14 = 0; var14 < var12.length; ++var14) {
                        var11[var14] ^= var12[var14];
                    }
                }

                var4.put(var11, 0, Math.min(var11.length, var4.capacity() - var9));
                var9 += var11.length;
            }

            return 1;
        } else {
            throw new IllegalArgumentException("algorithmName, password, and destination must not be null.");
        }
    }

    private static void writeBigEndianInt(int var0, byte[] var1) {
        var1[0] = (byte)(var0 >> 24 & 255);
        var1[1] = (byte)(var0 >> 16 & 255);
        var1[2] = (byte)(var0 >> 8 & 255);
        var1[3] = (byte)(var0 & 255);
    }
}
