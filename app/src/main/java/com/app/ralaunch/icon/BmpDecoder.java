package com.app.ralaunch.icon;

import android.graphics.Bitmap;

import com.app.ralaunch.utils.AppLogger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * BMP 格式解码器
 * 专门用于解码 Windows 图标中的 BMP 数据
 */
public class BmpDecoder {
    private static final String TAG = "BmpDecoder";
    
    /**
     * 解码图标中的 BMP 数据为 Android Bitmap
     * 
     * @param data BMP 数据（从 PE 资源中提取的原始数据）
     * @return Android Bitmap 对象，失败返回 null
     */
    public static Bitmap decodeBmpIcon(byte[] data) {
        try {
            if (data == null || data.length < 40) {
                AppLogger.error(TAG, "Invalid icon data: too short");
                return null;
            }
            
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            
            // Windows 图标资源中的 BMP 没有 BITMAPFILEHEADER (14 bytes)
            // 直接从 BITMAPINFOHEADER 开始 (40 bytes)
            
            // 读取 BITMAPINFOHEADER
            int headerSize = buffer.getInt(0);
            int width = buffer.getInt(4);
            int height = buffer.getInt(8);
            int planes = buffer.getShort(12) & 0xFFFF;
            int bitCount = buffer.getShort(14) & 0xFFFF;
            int compression = buffer.getInt(16);
            int imageSize = buffer.getInt(20);
            
            // 验证header size
            if (headerSize != 40) {
                AppLogger.error(TAG, "Invalid BITMAPINFOHEADER size: " + headerSize);
                return null;
            }
            
            // 图标的高度是实际高度的2倍（包含 AND mask）
            int actualHeight = height / 2;

            // 只支持常见的位深度
            if (bitCount != 32 && bitCount != 24 && bitCount != 8 && bitCount != 4 && bitCount != 1) {
                AppLogger.error(TAG, "Unsupported bit count: " + bitCount);
                return null;
            }
            
            // 创建 Bitmap
            Bitmap bitmap = Bitmap.createBitmap(width, actualHeight, Bitmap.Config.ARGB_8888);
            
            // 解码像素数据
            int pixelDataOffset = headerSize;
            
            // 如果有调色板
            if (bitCount <= 8) {
                int colorTableSize = (1 << bitCount) * 4; // 每个颜色4字节 (BGRA)
                pixelDataOffset += colorTableSize;
            }
            
            // 根据位深度解码
            switch (bitCount) {
                case 32:
                    decode32Bit(buffer, pixelDataOffset, bitmap, width, actualHeight);
                    break;
                case 24:
                    decode24Bit(buffer, pixelDataOffset, bitmap, width, actualHeight);
                    break;
                case 8:
                    decode8Bit(buffer, headerSize, pixelDataOffset, bitmap, width, actualHeight);
                    break;
                case 4:
                    decode4Bit(buffer, headerSize, pixelDataOffset, bitmap, width, actualHeight);
                    break;
                case 1:
                    decode1Bit(buffer, headerSize, pixelDataOffset, bitmap, width, actualHeight);
                    break;
            }
            
            return bitmap;
            
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to decode BMP: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 解码 32-bit BMP (BGRA)
     */
    private static void decode32Bit(ByteBuffer buffer, int offset, Bitmap bitmap, int width, int height) {
        int[] pixels = new int[width * height];
        
        // BMP 是从下到上存储的
        for (int y = height - 1; y >= 0; y--) {
            int rowOffset = offset + (height - 1 - y) * width * 4;
            for (int x = 0; x < width; x++) {
                int pixelOffset = rowOffset + x * 4;
                
                int b = buffer.get(pixelOffset) & 0xFF;
                int g = buffer.get(pixelOffset + 1) & 0xFF;
                int r = buffer.get(pixelOffset + 2) & 0xFF;
                int a = buffer.get(pixelOffset + 3) & 0xFF;
                
                pixels[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }
    
    /**
     * 解码 24-bit BMP (BGR)
     */
    private static void decode24Bit(ByteBuffer buffer, int offset, Bitmap bitmap, int width, int height) {
        int[] pixels = new int[width * height];
        int rowPadding = (4 - (width * 3) % 4) % 4; // 每行对齐到4字节
        
        for (int y = height - 1; y >= 0; y--) {
            int rowOffset = offset + (height - 1 - y) * (width * 3 + rowPadding);
            for (int x = 0; x < width; x++) {
                int pixelOffset = rowOffset + x * 3;
                
                int b = buffer.get(pixelOffset) & 0xFF;
                int g = buffer.get(pixelOffset + 1) & 0xFF;
                int r = buffer.get(pixelOffset + 2) & 0xFF;
                
                pixels[y * width + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }
    
    /**
     * 解码 8-bit BMP (indexed color)
     */
    private static void decode8Bit(ByteBuffer buffer, int headerSize, int offset, 
                                   Bitmap bitmap, int width, int height) {
        // 读取调色板
        int[] palette = new int[256];
        for (int i = 0; i < 256; i++) {
            int paletteOffset = headerSize + i * 4;
            int b = buffer.get(paletteOffset) & 0xFF;
            int g = buffer.get(paletteOffset + 1) & 0xFF;
            int r = buffer.get(paletteOffset + 2) & 0xFF;
            int a = buffer.get(paletteOffset + 3) & 0xFF;
            palette[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        
        int[] pixels = new int[width * height];
        int rowPadding = (4 - width % 4) % 4;
        
        for (int y = height - 1; y >= 0; y--) {
            int rowOffset = offset + (height - 1 - y) * (width + rowPadding);
            for (int x = 0; x < width; x++) {
                int index = buffer.get(rowOffset + x) & 0xFF;
                pixels[y * width + x] = palette[index];
            }
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }
    
    /**
     * 解码 4-bit BMP (16 colors)
     */
    private static void decode4Bit(ByteBuffer buffer, int headerSize, int offset, 
                                   Bitmap bitmap, int width, int height) {
        // 读取调色板
        int[] palette = new int[16];
        for (int i = 0; i < 16; i++) {
            int paletteOffset = headerSize + i * 4;
            int b = buffer.get(paletteOffset) & 0xFF;
            int g = buffer.get(paletteOffset + 1) & 0xFF;
            int r = buffer.get(paletteOffset + 2) & 0xFF;
            int a = buffer.get(paletteOffset + 3) & 0xFF;
            palette[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        
        int[] pixels = new int[width * height];
        int bytesPerRow = (width + 1) / 2;
        int rowPadding = (4 - bytesPerRow % 4) % 4;
        
        for (int y = height - 1; y >= 0; y--) {
            int rowOffset = offset + (height - 1 - y) * (bytesPerRow + rowPadding);
            for (int x = 0; x < width; x++) {
                int byteOffset = rowOffset + x / 2;
                int pixelByte = buffer.get(byteOffset) & 0xFF;
                int index = (x % 2 == 0) ? (pixelByte >> 4) : (pixelByte & 0x0F);
                pixels[y * width + x] = palette[index];
            }
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }
    
    /**
     * 解码 1-bit BMP (monochrome)
     */
    private static void decode1Bit(ByteBuffer buffer, int headerSize, int offset, 
                                   Bitmap bitmap, int width, int height) {
        // 读取调色板（通常是黑白）
        int[] palette = new int[2];
        for (int i = 0; i < 2; i++) {
            int paletteOffset = headerSize + i * 4;
            int b = buffer.get(paletteOffset) & 0xFF;
            int g = buffer.get(paletteOffset + 1) & 0xFF;
            int r = buffer.get(paletteOffset + 2) & 0xFF;
            int a = buffer.get(paletteOffset + 3) & 0xFF;
            palette[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        
        int[] pixels = new int[width * height];
        int bytesPerRow = (width + 7) / 8;
        int rowPadding = (4 - bytesPerRow % 4) % 4;
        
        for (int y = height - 1; y >= 0; y--) {
            int rowOffset = offset + (height - 1 - y) * (bytesPerRow + rowPadding);
            for (int x = 0; x < width; x++) {
                int byteOffset = rowOffset + x / 8;
                int pixelByte = buffer.get(byteOffset) & 0xFF;
                int bitIndex = 7 - (x % 8);
                int index = (pixelByte >> bitIndex) & 1;
                pixels[y * width + x] = palette[index];
            }
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }
}
