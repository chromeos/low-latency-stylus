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

import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_TRIANGLES;
import static android.opengl.GLES20.GL_UNSIGNED_INT;

import static dev.chromeos.lowlatencystylusdemo.gpu.DrawPoints.NUM_INDICES_PER_SQUARE;
import static dev.chromeos.lowlatencystylusdemo.gpu.DrawPoints.TEXTURE_COLOR_SIZE;
import static dev.chromeos.lowlatencystylusdemo.gpu.DrawPoints.TEXTURE_COORDINATE_SIZE;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLException;
import android.opengl.GLUtils;
import android.util.Log;

import javax.microedition.khronos.opengles.GL10;

/**
 * Handles the OpenGL rendering of a brush stroke
 *
 * Applies the matrix transformation and colours the stroke with the a_color information
 */
public class BrushShader {
    public static final float BRUSH_SIZE = 3.5f;
    private static final String TAG = "InkStrokeRenderer";
    private static final String VERTEX_SHADER =
            "attribute vec4 a_color;\n"
                    + "attribute vec4 a_position;\n"
                    + "varying vec4 v_color;\n"
                    + "uniform mat4 u_mvp;\n"
                    + "void main() {\n"
                    + "  vec4 pointPos = u_mvp * a_position;\n"
                    + "  gl_Position = vec4(pointPos.xy, 0.0, 1.0);\n"
                    + "  v_color = a_color;\n"
                    + "}\n";

    private static final String FRAG_SHADER =
            "precision mediump float;\n"
                    + "varying vec4 v_color;\n"
                    + "void main() {\n"
                    + "  gl_FragColor = v_color;\n"
                    + "}\n";

    /**
     * The Bitmap-based Brush shaders take in a bitmap file. For a pure black color, the fragment
     * shader uses the selected brush color and bitmap transparency. All colors in the bitmap
     * are ignored.
     *
     * Note: bitmap transparency is stepped to be either 0% or 100% to prevent unexpected darkening
     * due front/back-buffer rendering.
     *
     * TODO: allow for proper transparency in bitmap brushes
     */
    private static final String BITMAP_BRUSH_VERTEX_SHADER =
                      "attribute vec4 a_color;\n"
                    + "attribute vec4 a_position;\n"
                    + "uniform mat4 u_mvp;\n"
                    + "varying vec4 v_color;\n"
                    + "attribute vec2 a_texture_coord;\n"
                    + "varying vec2 v_texture_coord_from_shader;\n"
                    + "void main() {\n"
                    + "  vec4 pointPos = u_mvp * a_position;\n"
                    + "  v_color = a_color;\n"
                    + "  gl_Position = vec4(pointPos.xy, 0.0, 1.0);\n"
                    + "  v_texture_coord_from_shader = vec2(a_texture_coord.x, a_texture_coord.y);\n"
                    + "}\n";

    private static final String BITMAP_BRUSH_FRAG_SHADER =
                  "precision mediump float;\n"
                + "uniform sampler2D texture_sampler;\n"
                + "varying vec4 v_color;\n"
                + "varying vec2 v_texture_coord_from_shader;\n"
                    + "void main() {\n"
                    + "  vec4 tex_color = texture2D(texture_sampler, v_texture_coord_from_shader);\n"
                          + "  // Only use alpha value from bitmap to indicate draw area\n"
                          + "  // Step alpha 0 or 1 to avoid unintended lightening/darkening\n"
                          + "  float tex_alpha = step(0.4, tex_color.a);\n"
                          + "  gl_FragColor = v_color * tex_alpha; // color the bitmap\n"
                    + "}\n";

    // Shader programs and variable handles
    private final int mLineProgram;
    private final int mLineVertexHandle; // a_position
    private final int mLineColorHandle; // a_color
    private final int mLineMVPHandle; // u_mvp

    private final int mSprayPaintProgram;
    private final int mSprayPaintVertexHandle; // a_position
    private final int mSprayPaintTextureHandle; // a_texture_coord
    private final int mSprayPaintColorHandle; // a_color
    private final int mSprayPaintMVPHandle; // u_mvp


    // Spraypaint bitmap texture
    private boolean useSprayPaint = false;
    private boolean isSprayPaintTextureInitialized = false;
    private int[] sprayPaintTextures = new int[1];
    public void initSprayPaintTexture(GL10 gl, Bitmap bitmap) {
        gl.glGenTextures(1, sprayPaintTextures, 0);
        gl.glBindTexture(GL10.GL_TEXTURE_2D, sprayPaintTextures[0]);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        gl.glClearColor(1f, 1f, 1f, 1f);
        gl.glEnable(GL10.GL_BLEND);
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
        isSprayPaintTextureInitialized = true;
    }

    /**
     *
     * @param useSprayPaint enable or disable spraypaint
     * @return true if enable/disable was successful. Return false if spray paint texture has not
     * been previously initialized with initSprayPaintTexture
     */
    public boolean enableSprayPaint(boolean useSprayPaint) {
        // If spray paint bitmap not loaded, do not engage spray paint
        if (!isSprayPaintTextureInitialized) {
            useSprayPaint = false;
            return false;
        }
        this.useSprayPaint = useSprayPaint;
        return true;
    }

    private final DrawPoints mDrawPoints = new DrawPoints();

    public BrushShader() {

        // Create line shader and connect shader variables to handles
        mLineProgram = createLineShader();
        mLineColorHandle = GLES20.glGetAttribLocation(mLineProgram, "a_color");
        mLineVertexHandle = GLES20.glGetAttribLocation(mLineProgram, "a_position");
        mLineMVPHandle = GLES20.glGetUniformLocation(mLineProgram, "u_mvp");
        checkGlError("GL Get Locations");

        // Create spray paint shader and connect shader variables to handles
        mSprayPaintProgram = createSprayPaintShader();
        mSprayPaintColorHandle = GLES20.glGetAttribLocation(mSprayPaintProgram, "a_color");
        mSprayPaintVertexHandle = GLES20.glGetAttribLocation(mSprayPaintProgram, "a_position");
        mSprayPaintTextureHandle = GLES20.glGetAttribLocation(mSprayPaintProgram, "a_texture_coord");
        mSprayPaintMVPHandle = GLES20.glGetUniformLocation(mSprayPaintProgram, "u_mvp");
        checkGlError("GL Get Locations");
    }

    /**
     * Perform the draw
     *
     * mDrawPoints contains all the completed brush strokes and, separately, the predicted strokes.
     * Before drawing, added predicted strokes to the draw list temporarily and removed the after
     * the draw is finished.
     *
     * @param matrix MVP matrix (Model-View-Projection) used for current buffer
     */
    public void draw(float[] matrix) {
        // If nothing to draw, exit early
        if (mDrawPoints.count() == 0) {
            return;
        }

        // Current number of non-predicted points
        int tempVertexCount = mDrawPoints.count();

        // Remember current position at the end of the Vertex/Square buffers
        int tempVertexPosition = mDrawPoints.mVertexBuffer.position();
        int tempSquarePosition = mDrawPoints.mSquareBuffer.position();
        int tempTextureColorPosition = mDrawPoints.mTextureColorBuffer.position();
        int tempTextureCoordinatePosition = mDrawPoints.mTextureCoordinateBuffer.position();
        int tempSquareIndexPosition = mDrawPoints.mSquareIndexBuffer.position();

        // Spray paint shader
        if (useSprayPaint) {
            // Temporarily add the predicted points to the end of the draw list
            mDrawPoints.addPredictedDrawPointsForDraw(mDrawPoints.mPredictedDrawPoints);

            GLES20.glUseProgram(mSprayPaintProgram);

            // Skip the error check here because "draw" is a performance critical operation
            // checkGlError("GL Error in draw()");

            // Indices for glDrawElements
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mDrawPoints.mGLSquareIndexBufferHandle);
            GLES20.glBufferData(
                    GLES20.GL_ELEMENT_ARRAY_BUFFER, mDrawPoints.count() * DrawPoints.SQUARE_INDICES_SIZE, mDrawPoints.mSquareIndexBuffer.getRawByteBuffer(), GLES20.GL_STATIC_DRAW);

            // Vertices, including the prediction
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mDrawPoints.mGLSquareBufferHandle);
            GLES20.glBufferData(
                    GLES20.GL_ARRAY_BUFFER, mDrawPoints.count() * DrawPoints.Square.TOTAL_SIZE, mDrawPoints.mSquareBuffer.getRawByteBuffer(), GLES20.GL_STATIC_DRAW);
            GLES20.glVertexAttribPointer(
                    mSprayPaintVertexHandle,
                    2,
                    GL_FLOAT,
                    false,
                    0,
                    0);
            GLES20.glEnableVertexAttribArray(mSprayPaintVertexHandle);

            // Texture coords
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mDrawPoints.mGLTextureCoordinateBufferHandle);
            GLES20.glBufferData(
                    GLES20.GL_ARRAY_BUFFER, mDrawPoints.count() * TEXTURE_COORDINATE_SIZE, mDrawPoints.mTextureCoordinateBuffer.getRawByteBuffer(), GLES20.GL_STATIC_DRAW);
            GLES20.glVertexAttribPointer(
                    mSprayPaintTextureHandle,
                    2,
                    GL_FLOAT,
                    false,
                    0,
                    0);
            GLES20.glEnableVertexAttribArray(mSprayPaintTextureHandle);

            // Brush colors
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mDrawPoints.mGLTextureColorBufferHandle);
            GLES20.glBufferData(
                    GLES20.GL_ARRAY_BUFFER, mDrawPoints.count() * TEXTURE_COLOR_SIZE, mDrawPoints.mTextureColorBuffer.getRawByteBuffer(), GLES20.GL_STATIC_DRAW);
            GLES20.glVertexAttribPointer(
                    mSprayPaintColorHandle,
                    4,
                    GL_FLOAT,
                    false,
                    0,
                    0);
            GLES20.glEnableVertexAttribArray(mSprayPaintColorHandle);

            // Enable the MVP matrix
            GLES20.glUniformMatrix4fv(mSprayPaintMVPHandle, 1, false, matrix, 0);

            // Draw the bitmap textures
            GLES20.glActiveTexture(GL10.GL_TEXTURE0);
            GLES20.glBindTexture(GL10.GL_TEXTURE_2D, sprayPaintTextures[0]);
            GLES20.glDrawElements( GL_TRIANGLES, mDrawPoints.count() * NUM_INDICES_PER_SQUARE, GL_UNSIGNED_INT, 0);

            GLES20.glDisableVertexAttribArray(mSprayPaintVertexHandle);
            GLES20.glDisableVertexAttribArray(mSprayPaintTextureHandle);
            GLES20.glDisableVertexAttribArray(mSprayPaintColorHandle);

        } else { // Else draw with the regular line shader
            // Temporarily add the predicted points to the end of the draw list
            mDrawPoints.addPredictedVerticesForDraw(mDrawPoints.mPredictedVertices);

            GLES20.glUseProgram(mLineProgram);

            // Skip the error check here because "draw" is a performance critical operation
            // checkGlError("GL Error in draw()");

            // Draw the vertices into the GL vertex buffer, including the predicted ones
            // 2 * draw points for GL_LINES (each vertex needs start and finish point)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mDrawPoints.mGLVertexBufferHandle);
            GLES20.glBufferData(
                    GLES20.GL_ARRAY_BUFFER, mDrawPoints.count() * 2 * DrawPoints.Vertex.TOTAL_SIZE, mDrawPoints.mVertexBuffer.getRawByteBuffer(), GLES20.GL_STATIC_DRAW);

            // Prepare the triangle coordinate data
            GLES20.glVertexAttribPointer(
                    mLineVertexHandle,
                    DrawPoints.Vertex.POSITION_DIM,
                    GL_FLOAT,
                    false,
                    DrawPoints.Vertex.TOTAL_SIZE,
                    DrawPoints.Vertex.POSITION_OFFSET);
            GLES20.glEnableVertexAttribArray(mLineVertexHandle);

            // Color info
            GLES20.glVertexAttribPointer(
                    mLineColorHandle,
                    DrawPoints.Vertex.COLOR_DIM,
                    GL_FLOAT,
                    false,
                    DrawPoints.Vertex.TOTAL_SIZE,
                    DrawPoints.Vertex.COLOR_OFFSET);
            GLES20.glEnableVertexAttribArray(mLineColorHandle);

            // Enable the MVP matrix
            GLES20.glUniformMatrix4fv(mLineMVPHandle, 1, false, matrix, 0);

            // Note: OpenGL does not need to respect brush size > 1px
            // 2 * draw points for GL_LINES (each vertex needs start and finish point)
            GLES20.glLineWidth(BRUSH_SIZE);
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, mDrawPoints.count() * 2);

            GLES20.glDisableVertexAttribArray(mLineVertexHandle);
            GLES20.glDisableVertexAttribArray(mLineColorHandle);
        }

        // Restore the original position/size of the Vertex buffer (to remove predicted lines)
        mDrawPoints.mVertexBuffer.position(tempVertexPosition);
        mDrawPoints.mSquareBuffer.position(tempSquarePosition);
        mDrawPoints.mTextureCoordinateBuffer.position(tempTextureCoordinatePosition);
        mDrawPoints.mTextureColorBuffer.position(tempTextureColorPosition);
        mDrawPoints.mSquareIndexBuffer.position(tempSquareIndexPosition);
        mDrawPoints.count(tempVertexCount);

        // Clear predicted strokes from the prediction lists after they've been drawn
        mDrawPoints.clearPrediction();
    }

    // Pass-through functions for drawing points' buffer and array management
    public void addDrawPoint(DrawPoint drawPoint) {
        mDrawPoints.addDrawPoint(drawPoint);
    }
    public void addPredictionDrawPoint(DrawPoint drawPoint) {
        mDrawPoints.addPredictionDrawPoint(drawPoint);
    }
    public void clear() {
        mDrawPoints.clear();
    }
    public void endLine() {
        mDrawPoints.endStroke();
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

    private int createLineShader() {
        // Create the line shader program.
        int lineProgram = GLES20.glCreateProgram();
        if (lineProgram == 0) {
            checkGlError("Error creating line shader program");
        }

        // Load the vertex shader.
        final int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        // Load the fragment shader.
        final int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAG_SHADER);

        // Attach the vertex shader to the shader program.
        GLES20.glAttachShader(lineProgram, vertexShader);
        checkGlError("Error attaching the vertex shader");

        // Attach the fragment shader to the shader program.
        GLES20.glAttachShader(lineProgram, fragmentShader);
        checkGlError("Error attaching the fragment shader");

        // Link the shader program.
        GLES20.glLinkProgram(lineProgram);
        checkGlError("Error linking the shader program");

        return lineProgram;
    }

    private int createSprayPaintShader() {
        // Init spray paint shader
        // Create the spray paint shader program.
        int sprayPaintProgram = GLES20.glCreateProgram();
        if (sprayPaintProgram == 0) {
            checkGlError("Error creating a spray paint shader program");
        }

        // Load the vertex shader.
        final int sprayVertexShader = loadShader(GLES20.GL_VERTEX_SHADER, BITMAP_BRUSH_VERTEX_SHADER);
        // Load the fragment shader.
        final int sprayFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, BITMAP_BRUSH_FRAG_SHADER);

        // Attach the vertex shader to the shader program.
        GLES20.glAttachShader(sprayPaintProgram, sprayVertexShader);
        checkGlError("Error attaching the vertex shader");

        // Attach the fragment shader to the shader program.
        GLES20.glAttachShader(sprayPaintProgram, sprayFragmentShader);
        checkGlError("Error attaching the fragment shader");

        // Link the shader program.
        GLES20.glLinkProgram(sprayPaintProgram);
        checkGlError("Error linking the shader program");

        return sprayPaintProgram;
    }

    private void checkGlError(String msg) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            GLException exception = new GLException(error, msg);
            Log.e(TAG, "OpenGl error: " + error, exception);
            throw exception;
        }
    }
}
