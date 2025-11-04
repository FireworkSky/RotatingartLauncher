package com.app.ralaunch.utils;

public class StreamUtils {
    private StreamUtils() {}

    /**
     * 使用默认缓冲区大小(8192字节)传输数据
     */
    public static void transferTo(java.io.InputStream input, java.io.OutputStream output) throws java.io.IOException {
        transferTo(input, output, 8192);
    }

    /**
     * 使用指定缓冲区大小传输数据
     * @param input 输入流
     * @param output 输出流
     * @param blockSize 缓冲区大小(字节)
     */
    public static void transferTo(java.io.InputStream input, java.io.OutputStream output, int blockSize) throws java.io.IOException {
        if (blockSize <= 0) {
            throw new IllegalArgumentException("Block size must be greater than 0");
        }
        byte[] buffer = new byte[blockSize];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }
}
