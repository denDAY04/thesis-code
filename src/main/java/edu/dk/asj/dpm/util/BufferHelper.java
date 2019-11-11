package edu.dk.asj.dpm.util;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class BufferHelper {

    public static byte[] readAndClear(ByteBuffer buffer) {
        int length;
        if (buffer.position() != 0) {
            length = buffer.position();
            buffer.flip();
        } else {
            length = buffer.limit();
        }
        byte[] data = new byte[length];
        buffer.get(data).clear();
        return data;
    }

    public static int[] readAndClear(IntBuffer buffer) {
        int length;
        if (buffer.position() != 0) {
            length = buffer.position();
            buffer.flip();
        } else {
            length = buffer.limit();
        }
        int[] data = new int[length];
        buffer.get(data).clear();
        return data;
    }
}
