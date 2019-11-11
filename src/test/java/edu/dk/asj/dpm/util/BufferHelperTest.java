package edu.dk.asj.dpm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BufferHelperTest {

    @Test
    @DisplayName("Get data from byte buffer")
    void returnByteData() {
        byte b1 = (byte) 0x01;
        byte b2 = (byte) 0x11;

        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put(b1);
        buffer.put(b2);

        byte[] data = BufferHelper.readAndClear(buffer);
        assertNotNull(data, "Data array is null");
        assertEquals(2, data.length, "Unexpected data array length");
        assertEquals(b1, data[0], "Unexpected first data byte");
        assertEquals(b2, data[1], "Unexpected second data byte");
    }

    @Test
    @DisplayName("Get data from int buffer")
    void returnIntData() {
        int i1 = 12;
        int i2 = 42;

        IntBuffer buffer = IntBuffer.allocate(10);
        buffer.put(i1);
        buffer.put(i2);

        int[] data = BufferHelper.readAndClear(buffer);
        assertNotNull(data, "Data array is null");
        assertEquals(2, data.length, "Unexpected data array length");
        assertEquals(i1, data[0], "Unexpected first data int");
        assertEquals(i2, data[1], "Unexpected second data int");
    }

    @Test
    @DisplayName("Get data from random-access buffer")
    void returnFromRandom() {
        byte b1 = (byte) 0x01;
        byte b2 = (byte) 0x11;
        byte b3 = (byte) 0x07;

        ByteBuffer buffer = ByteBuffer.allocate(3);
        buffer.put(0, b1);
        buffer.put(2, b3);
        buffer.put(1, b2);

        byte[] data = BufferHelper.readAndClear(buffer);
        assertNotNull(data, "Data array is null");
        assertEquals(3, data.length, "Unexpected data array length");
        assertEquals(b1, data[0], "Unexpected first data byte");
        assertEquals(b2, data[1], "Unexpected second data byte");
        assertEquals(b3, data[2], "Unexpected third data byte");
    }

}