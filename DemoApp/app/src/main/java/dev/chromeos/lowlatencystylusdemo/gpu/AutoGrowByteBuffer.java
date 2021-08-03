/*
 * Copyright (c) 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.chromeos.lowlatencystylusdemo.gpu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * An auto growing byte buffer
 */
public class AutoGrowByteBuffer {
    private static final int INTEGER_BYTE_COUNT = Integer.SIZE / Byte.SIZE;
    private static final int LONG_BYTE_COUNT = Long.SIZE / Byte.SIZE;
    private static final int FLOAT_BYTE_COUNT = Float.SIZE / Byte.SIZE;
    private static final int DOUBLE_BYTE_COUNT = Double.SIZE / Byte.SIZE;
    private static final int DEFAULT_INITIAL_BUFFER_SIZE = 1024;
    private ByteBuffer mBuffer;

    public AutoGrowByteBuffer() {
        this(DEFAULT_INITIAL_BUFFER_SIZE);
    }
    public AutoGrowByteBuffer(int initialCapacity) {
        if (initialCapacity < 0) initialCapacity = 0;
        mBuffer = ByteBuffer.allocateDirect(initialCapacity);
        mBuffer.order(ByteOrder.nativeOrder());
    }
    public ByteBuffer getRawByteBuffer() {
        // Slice the buffer from 0 to position.
        int limit = mBuffer.limit();
        int position = mBuffer.position();
        mBuffer.limit(position);
        mBuffer.position(0);
        ByteBuffer buffer = mBuffer.slice();

        // Restore position and limit.
        mBuffer.limit(limit);
        mBuffer.position(position);

        return buffer;
    }
    public ByteOrder order() {
        return mBuffer.order();
    }
    public int position() {
        return mBuffer.position();
    }
    public AutoGrowByteBuffer position(int newPosition) {
        mBuffer.position(newPosition);
        return this;
    }
    public AutoGrowByteBuffer order(ByteOrder order) {
        mBuffer.order(order);
        return this;
    }
    public AutoGrowByteBuffer putInt(int value) {
        ensureCapacity(INTEGER_BYTE_COUNT);
        mBuffer.putInt(value);
        return this;
    }
    public AutoGrowByteBuffer putLong(long value) {
        ensureCapacity(LONG_BYTE_COUNT);
        mBuffer.putLong(value);
        return this;
    }
    public AutoGrowByteBuffer putFloat(float value) {
        ensureCapacity(FLOAT_BYTE_COUNT);
        mBuffer.putFloat(value);
        return this;
    }
    public AutoGrowByteBuffer putDouble(double value) {
        ensureCapacity(DOUBLE_BYTE_COUNT);
        mBuffer.putDouble(value);
        return this;
    }
    public AutoGrowByteBuffer put(byte[] src) {
        ensureCapacity(src.length);
        mBuffer.put(src);
        return this;
    }
    public AutoGrowByteBuffer put(float[] src) {
        ensureCapacity(src.length * FLOAT_BYTE_COUNT);
        mBuffer.asFloatBuffer().put(src);
        // Put via FloatBuffer will not update position so manually update
        mBuffer.position(mBuffer.position() + (src.length * FLOAT_BYTE_COUNT));
        return this;
    }
    public AutoGrowByteBuffer put(int[] src) {
        ensureCapacity(src.length * INTEGER_BYTE_COUNT);
        mBuffer.asIntBuffer().put(src);
        // Put via IntBuffer will not update position so manually update
        mBuffer.position(mBuffer.position() + (src.length * INTEGER_BYTE_COUNT));
        return this;
    }
    /**
     * Ensures capacity to append at least <code>count</code> values.
     */
    private void ensureCapacity(int count) {
        if (mBuffer.remaining() < count) {
            int newCapacity = mBuffer.position() + count;
            if (newCapacity > Integer.MAX_VALUE >> 1) {
                throw new IllegalStateException(
                        "Item memory requirements too large: " + newCapacity);
            }
            newCapacity <<= 1; // Double capacity
            ByteBuffer buffer = ByteBuffer.allocateDirect(newCapacity);
            buffer.order(mBuffer.order());
            // Copy data from old buffer to new buffer
            mBuffer.flip();
            buffer.put(mBuffer);
            // Set buffer to new buffer
            mBuffer = buffer;
        }
    }
}