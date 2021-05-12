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

import android.opengl.GLES20;
import android.opengl.GLException;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles the OpenGL rendering of a brush stroke*
 */
public class BrushShader {
    // A vertex in this sample is a 2D-point followed by float RGB color values, range: 0.0 - 1.0
    public static class Vertex {
        public static final int FLOAT_SIZE = 4;
        public static final int POSITION_DIM = 2;
        public static final int COLOR_DIM = 3;
        public static final int TOTAL_DIM = POSITION_DIM + COLOR_DIM;
        public static final int POSITION_SIZE = POSITION_DIM * FLOAT_SIZE;
        public static final int COLOR_SIZE = COLOR_DIM * FLOAT_SIZE;
        public static final int TOTAL_SIZE = POSITION_SIZE + COLOR_SIZE;
        public static final int POSITION_OFFSET = 0;
        public static final int COLOR_OFFSET = POSITION_OFFSET + POSITION_SIZE;
        public float[] data = new float[TOTAL_DIM];

        public Vertex(float x, float y) {
            data[0] = x;
            data[1] = y;
            data[2] = .0f;
            data[3] = .0f;
            data[4] = .0f;
        }

        public void setColor(float r, float g, float b) {
            data[2] = r;
            data[3] = g;
            data[4] = b;
        }
    }

    public static final float BRUSH_SIZE = 3.5f;
    private static final int DEFAULT_BUFFER_SIZE = 512;

    private static final String TAG = "InkStrokeRenderer";
    private static final String VERTEX_SHADER =
            "attribute vec3 a_color;\n"
                    + "attribute vec4 a_position;\n"
                    + "varying vec3 v_color;\n"
                    + "uniform mat4 u_mvp;\n"
                    + "void main() {\n"
                    + "  vec4 pointPos = u_mvp * a_position;\n"
                    + "  gl_Position = vec4(pointPos.xy, 0.0, 1.0);\n"
                    + "  v_color = a_color;\n"
                    + "}\n";

    private static final String FRAG_SHADER =
            "precision mediump float;\n"
                    + "varying vec3 v_color;\n"
                    + "void main() {\n"
                    + "  gl_FragColor = vec4(v_color, 1.0);\n"
                    + "}\n";

    // Shader program
    private final int mProgram;
    // Handle for "a_position".
    private final int mVertexAttribHandle;
    // Handle for "v_color".
    private final int mColorHandle;
    // Handle for "u_mvp".
    private final int mMVPHandle;

    // Reference to the buffer used by GL to draw the vertices
    private final int mGLVertexBufferHandle;
    private final List<Vertex> mPredictedVertex;

    // Buffer to hold Vertex's of drawn points. Will not contain predicted points
    private FloatBuffer mVertexBuffer;
    // Number of Vertex's in the current buffer
    private int mVertexCount;
    // Size (in Vertex count) currently allocated to the VertexBuffer
    private int mVertexBufferSize = DEFAULT_BUFFER_SIZE;

    public BrushShader() {
        // List of predicted points
        mPredictedVertex = new ArrayList<>();

        // Set up the draw buffer
        ByteBuffer bb = ByteBuffer.allocateDirect(mVertexBufferSize * Vertex.TOTAL_SIZE);
        bb.order(ByteOrder.nativeOrder());
        mVertexBuffer = bb.asFloatBuffer();
        mVertexBuffer.position(0);

        // Create the shader program.
        mProgram = GLES20.glCreateProgram();
        if (mProgram == 0) {
            checkGlError("Error creating a new shader program");
        }

        // Load the vertex shader.
        final int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        // Load the fragment shader.
        final int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAG_SHADER);

        // Attach the vertex shader to the shader program.
        GLES20.glAttachShader(mProgram, vertexShader);
        checkGlError("Error attaching the vertex shader");

        // Attach the fragment shader to the shader program.
        GLES20.glAttachShader(mProgram, fragmentShader);
        checkGlError("Error attaching the fragment shader");

        // Link the shader program.
        GLES20.glLinkProgram(mProgram);
        checkGlError("Error linking the shader program");

        // Link the shader program variables to handles
        mColorHandle = GLES20.glGetAttribLocation(mProgram, "a_color");
        mVertexAttribHandle = GLES20.glGetAttribLocation(mProgram, "a_position");
        mMVPHandle = GLES20.glGetUniformLocation(mProgram, "u_mvp");
        checkGlError("GL Get Locations");

        // Allocate space for the Vertex buffer (will auto-grow as needed in addVertex)
        IntBuffer tmp = IntBuffer.allocate(1);
        GLES20.glGenBuffers(1, tmp);
        mGLVertexBufferHandle = tmp.get();
        mVertexBufferSize = DEFAULT_BUFFER_SIZE;
    }

    /**
     * Perform the draw
     *
     * mVertexBuffer holds real gestures already made. mPredictedVertex holds predicted gestures
     * for the current stroke. Before drawing, add the predicted gestures to mVertexBuffer, draw
     * them, and then remove them. This wil ensure predicted gestures are "replaced" by real
     * gestures in the next frame.
     *
     * @param matrix MVP matrix (Model-View-Projection) used for current buffer
     */
    public void draw(float[] matrix) {
        // If nothing to draw, exit early
        if (mVertexCount == 0) {
            return;
        }

        // Current number of non-predicted points
        int tempCount = mVertexCount;
        // Current position at the end of the Vertex buffer
        int tempPosition = mVertexBuffer.position();

        // Temporarily add the predicted points to the end of list
        addVertices(mPredictedVertex);
        // Adjust count to match
        // mVertexCount += mPredictedVertex.size();

        // Rewind buffer to the beginning for the draw
        mVertexBuffer.position(0);
        // Skip the error check here because "draw" is a performance critical operation
        GLES20.glUseProgram(mProgram);

        // Draw the vertices into the GL vertex buffer, including the predicted ones
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mGLVertexBufferHandle);
        GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER, mVertexCount * Vertex.TOTAL_SIZE, mVertexBuffer, GLES20.GL_STATIC_DRAW);

        // Prepare the triangle coordinate data
        GLES20.glVertexAttribPointer(
                mVertexAttribHandle,
                Vertex.POSITION_DIM,
                GLES20.GL_FLOAT,
                false,
                Vertex.TOTAL_SIZE,
                Vertex.POSITION_OFFSET);
        GLES20.glEnableVertexAttribArray(mVertexAttribHandle);

        GLES20.glVertexAttribPointer(
                mColorHandle,
                Vertex.COLOR_DIM,
                GLES20.GL_FLOAT,
                false,
                Vertex.TOTAL_SIZE,
                Vertex.COLOR_OFFSET);
        GLES20.glEnableVertexAttribArray(mColorHandle);
        GLES20.glUniformMatrix4fv(mMVPHandle, 1, false, matrix, 0);

        GLES20.glLineWidth(BRUSH_SIZE);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, mVertexCount);
        GLES20.glDisableVertexAttribArray(mVertexAttribHandle);
        GLES20.glDisableVertexAttribArray(mColorHandle);

        // Restore the original position/size of the Vertex buffer ("erasing" predicted gestures)
        mVertexBuffer.position(tempPosition);
        mVertexCount = tempCount;

        // Clear predicted lines after they've been drawn
        mPredictedVertex.clear();
    }

    // Add a Vertex to the Vertex buffer, grow as required
    public void addVertex(Vertex vertex) {
        mVertexBuffer.put(vertex.data);
        mVertexCount++;
        growSizeAndReallocate();
    }

    public void addVertices(Iterable<Vertex> vertices) {
        for (Vertex v : vertices) {
            addVertex(v);
        }
    }

    public void addPredictionVertex(Vertex v) {
        mPredictedVertex.add(v);
    }

    // reset buffers
    public void clear() {
        clearPrediction();
        mVertexBuffer.position(0);
        mVertexCount = 0;
    }

    private void clearPrediction() {
        mPredictedVertex.clear();
    }

    private void growSizeAndReallocate() {
        if (mVertexCount < mVertexBufferSize) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(
                2 * mVertexBufferSize * Vertex.TOTAL_SIZE);
        buffer.order(ByteOrder.nativeOrder());
        FloatBuffer newBuffer = buffer.asFloatBuffer();
        newBuffer.position(0);

        int oldPosition = mVertexBuffer.position();
        mVertexBuffer.position(0);
        newBuffer.put(mVertexBuffer);
        mVertexBuffer = newBuffer;
        mVertexBuffer.position(oldPosition);

        mVertexBufferSize = 2 * mVertexBufferSize;
    }

    /**
     * Loads and compiles the given {@code shaderType} and {@code shaderCode}.
     *
     * @param shaderType the type of shader to be created
     * @param shaderCode source code to be loaded into the shader
     * @return an integer reference to the created shader
     * @throws RuntimeException an error while compiling the shader
     */
    private int loadShader(int shaderType, String shaderCode) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader == 0) {
            checkGlError("Error loading shader: " + shaderType);
        }

        // Add the source code to the shader and compile it.
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            String infoLog = GLES20.glGetShaderInfoLog(shader);
            Log.e(TAG, infoLog);
            GLES20.glDeleteShader(shader);
            throw new IllegalArgumentException("Shader compilation failed: " + infoLog);
        }
        return shader;
    }

    private void checkGlError(String msg) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            GLException exception = new GLException(error, msg);
            Log.e(TAG, "GlContext error:", exception);
            throw exception;
        }
    }
}
