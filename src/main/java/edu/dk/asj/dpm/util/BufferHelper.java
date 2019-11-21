package edu.dk.asj.dpm.util;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Helper class for {@link java.nio.Buffer} implementations.
 */
public class BufferHelper {

    /**
     * Read the contents of a byte buffer and reset the buffer.
     * @param buffer the buffer to read from.
     * @return the content of the buffer.
     */
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

    /**
     * Read the contents of an int buffer and rest the buffer.
     * @param buffer the buffer to read from.
     * @return the content of the buffer.
     */
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
