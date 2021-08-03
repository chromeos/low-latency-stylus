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
import android.util.Log;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A wrapper class to manage all the opengl buffers needed to draw with both line-based and bitmap-
 * based brushes. Handles both committed and predicted points
 */
public class DrawPoints {
    // Convenience constants
    public static final int FLOAT_SIZE = Float.SIZE / Byte.SIZE;
    public static final int INT_SIZE = Float.SIZE / Byte.SIZE;
    public static final int NUM_TEXTURE_COORDINATES_PER_SQUARE = 8;
    public static final int TEXTURE_COORDINATE_SIZE = FLOAT_SIZE * NUM_TEXTURE_COORDINATES_PER_SQUARE;
    public static final int NUM_COLOR_VALUES_PER_SQUARE = 4 * 4; //rgba * 4 vertices
    public static final int TEXTURE_COLOR_SIZE = FLOAT_SIZE * NUM_COLOR_VALUES_PER_SQUARE;
    public static final int NUM_INDICES_PER_SQUARE = 6;
    public static final int SQUARE_INDICES_SIZE = INT_SIZE * NUM_INDICES_PER_SQUARE;
    public static final int DEFAULT_BUFFER_SIZE = 1024;

    private int mPointCount;

    // Keep track of previous vertex for line drawing operations
    Vertex mPreviousVertex = null;

    // List of predicted vertices
    public final List<DrawPoint> mPredictedDrawPoints;
    public final List<Vertex> mPredictedVertices;
    Vertex mPreviousPredictionVertex = null;


    // Buffer to hold Vertex's of drawn points. Will not contain predicted points
    public AutoGrowByteBuffer mVertexBuffer;
    // Reference to the buffer used by GL to draw the vertices
    public final int mGLVertexBufferHandle;

    // Buffer to hold data for texture squares
    public AutoGrowByteBuffer mSquareBuffer;
    // Reference to the buffer for texture squares
    public final int mGLSquareBufferHandle;

    // Buffer to hold data for texture coordinates
    public AutoGrowByteBuffer mTextureCoordinateBuffer;
    // Reference to the buffer for texture coordinates
    public final int mGLTextureCoordinateBufferHandle;

    // Buffer to hold color data for texture squares
    public AutoGrowByteBuffer mTextureColorBuffer;
    // Reference to the buffer for texture squares
    public final int mGLTextureColorBufferHandle;

    // Buffer to hold indices for texture squares
    public AutoGrowByteBuffer mSquareIndexBuffer;
    // Reference to the buffer for texture square indices
    public final int mGLSquareIndexBufferHandle;

    public DrawPoints() {
        // List of predicted points
        mPredictedDrawPoints = new ArrayList<>();
        mPredictedVertices = new ArrayList<>();

        // Vertex buffer for line shaders
        mVertexBuffer = new AutoGrowByteBuffer(DEFAULT_BUFFER_SIZE * Vertex.TOTAL_SIZE);
        IntBuffer tempVertexBuffer = IntBuffer.allocate(1);
        GLES20.glGenBuffers(1, tempVertexBuffer);
        mGLVertexBufferHandle = tempVertexBuffer.get();

        // Square buffer for bitmap shaders
        mSquareBuffer = new AutoGrowByteBuffer(DEFAULT_BUFFER_SIZE * Square.TOTAL_SIZE);
        IntBuffer tempSquareBuffer = IntBuffer.allocate(1);
        GLES20.glGenBuffers(1, tempSquareBuffer);
        mGLSquareBufferHandle = tempSquareBuffer.get();

        // Texture coordinate buffer for bitmap shaders
        mTextureCoordinateBuffer = new AutoGrowByteBuffer(DEFAULT_BUFFER_SIZE * TEXTURE_COORDINATE_SIZE);
        IntBuffer tempTextureCoordinateBuffer = IntBuffer.allocate(1);
        GLES20.glGenBuffers(1, tempTextureCoordinateBuffer);
        mGLTextureCoordinateBufferHandle = tempTextureCoordinateBuffer.get();

        // Texture color buffer for bitmap shaders
        mTextureColorBuffer = new AutoGrowByteBuffer(DEFAULT_BUFFER_SIZE * TEXTURE_COLOR_SIZE);
        IntBuffer tempTextureColorBuffer = IntBuffer.allocate(1);
        GLES20.glGenBuffers(1, tempTextureColorBuffer);
        mGLTextureColorBufferHandle = tempTextureColorBuffer.get();

        // Square indices buffer for bitmap shaders
        mSquareIndexBuffer = new AutoGrowByteBuffer(DEFAULT_BUFFER_SIZE * SQUARE_INDICES_SIZE);
        IntBuffer tempSquareIndexBuffer = IntBuffer.allocate(1);
        GLES20.glGenBuffers(1, tempSquareIndexBuffer);
        mGLSquareIndexBufferHandle = tempSquareIndexBuffer.get();
    }

    // Draw point count
    public int count() {
        return mPointCount;
    }
    public void count(int newCount) {
        mPointCount = newCount;
    }

    // Clear current drawing and predictions
    // Don't erase or re-allocate memory, just move the position pointers
    public void clear() {
        clearPrediction();
        mVertexBuffer.position(0);
        mSquareBuffer.position(0);
        mTextureCoordinateBuffer.position(0);
        mTextureColorBuffer.position(0);
        mSquareIndexBuffer.position(0);
        mPointCount = 0;
    }

    // Clear prediction lists
    public void clearPrediction() {
        mPredictedDrawPoints.clear();
        mPredictedVertices.clear();
    }

    // Line shaders need to keep track of last drawn point. Set last point to null to prevent
    // starting the next line from beginning from the last point if the pen has been lifted
    public void endStroke() {
        mPreviousVertex = null;
        mPreviousPredictionVertex = null;
    }

    /**
     * Add a point to the drawing list. For line brushes that use GL_LINES, two points must be
     * added to the draw buffer (start and finish) as well as color info.
     * For bitmap brushes, vertex, texture coordinates, color, and array indices need to be
     * generated.
     * @param drawPoint The float point to add to the drawing list
     */
    public void addDrawPoint(DrawPoint drawPoint) {
        // For bitmap shaders, just add one point
        addSquare(drawPoint);

        // For line shaders, add start and finish of gesture
        if (null == mPreviousVertex) {
            // This is the first point, queue it up as the beginning of the line
            mPreviousVertex = new Vertex(drawPoint);
            // Add same point twice to start off a new line
            addVertex(mPreviousVertex);
            addVertex(mPreviousVertex);
        } else {
        // This is the middle or end of gesture, draw from last point to the new point
            Vertex newVertex = new Vertex(drawPoint);
            // Add start and finish
            addVertex(mPreviousVertex);
            addVertex(newVertex);
            mPreviousVertex = newVertex;
        }
        mPointCount++;
    }

    // Add a predicted point to the prediction lists
    public void addPredictionDrawPoint(DrawPoint drawPoint) {
        // Add predicted drawpoints for bitmap shaders
        mPredictedDrawPoints.add(drawPoint);
        // Add predicted vertices for line shaders
        mPredictedVertices.add(new Vertex(drawPoint));
    }

    // Add a Vertex to the line shader buffer
    public void addVertex(Vertex vertex) {
        mVertexBuffer.put(vertex.data);
    }

    // Add a list of predicted vertices for the line shader
    public void addPredictedVerticesForDraw(Iterable<Vertex> vertices) {
        // If there is no line currently being drawn, do not add a prediction
        if (null != mPreviousVertex) {
            // Predictions can contain several points, if this is the first point of a prediction,
            // start at the last drawn point
            if (null == mPreviousPredictionVertex) {
                mPreviousPredictionVertex = mPreviousVertex;
            }

            for (Vertex v : vertices) {
                // For drawn lines, add vertices in groups of 2 (beginning and end)
                addVertex(mPreviousPredictionVertex);
                addVertex(v);
                mPreviousPredictionVertex = v;
                mPointCount++;
            }
        }
        mPreviousPredictionVertex = null;
    }

    // Add a list of predicted points to draw to the bitmap shader buffes
    public void addPredictedDrawPointsForDraw(Iterable<DrawPoint> drawPoints) {
        for (DrawPoint drawPoint : drawPoints) {
            // For drawn lines, add vertices in groups of 2 (beginning and end)
            addSquare(drawPoint);
            mPointCount++;
        }
    }

    // Add vertex, index, color, and texture coord info to the bitmap buffers
    public void addSquare(DrawPoint drawPoint) {
        Square s = new Square(drawPoint);
        mSquareBuffer.put(s.data);
        addTextureCoordinates();
        addTextureColor(drawPoint);
        addSquareIndices();
    }

    // Add a new texture coordinate value
    // Currently this is invariable for every square (sample the whole texture)
    private void addTextureCoordinates() {
        // Sample the whole texture
        float[] texture_coord = {
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f,
        };
        mTextureCoordinateBuffer.put(texture_coord);
    }

    // Add the draw point's color info to the texture color buffer
    private void addTextureColor(DrawPoint drawPoint) {
        addTextureColor(drawPoint.red, drawPoint.green, drawPoint.blue);
    }
    private void addTextureColor(float r, float g, float b) {
        float[] colors = { r, g, b, 1.0f };
        // Add colors 4 times, one for each vertex
        // Here is where gradients, etc. could be added
        mTextureColorBuffer.put(colors);
        mTextureColorBuffer.put(colors);
        mTextureColorBuffer.put(colors);
        mTextureColorBuffer.put(colors);
    }

    // Add indexes for bitmap shaders. This is 2 triangles / 4 corners that make up a square
    // For square 3 - 2  order is: 0, 1, 2, 2, 3, 0
    //            0 - 1
    private void addSquareIndices() {
        final int start = mPointCount * 4; // 4 corners to define a square
        // Two triangles that make up a square, starting at the current count of vertices
        int[] indices = {
                start, start + 1, start + 2,
                start + 2, start + 3, start
        };
        mSquareIndexBuffer.put(indices);
    }

    // A vertex is a 2D-point followed by float RGB color values, range: 0.0 - 1.0
    public static class Vertex {
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
            // Default to black
            data[POSITION_DIM] = .0f;
            data[POSITION_DIM + 1] = .0f;
            data[POSITION_DIM + 2] = .0f;
        }

        public Vertex(float x, float y, float r, float g, float b) {
            this(x, y);
            setColor(r, g, b);
        }

        public Vertex(DrawPoint drawPoint) {
            data[0] = drawPoint.point.x;
            data[1] = drawPoint.point.y;
            // Default to black
            data[POSITION_DIM] = drawPoint.red;
            data[POSITION_DIM + 1] = drawPoint.green;
            data[POSITION_DIM + 2] = drawPoint.blue;
        }
        public void setColor(float r, float g, float b) {
            data[POSITION_DIM] = r;
            data[POSITION_DIM + 1] = g;
            data[POSITION_DIM + 2] = b;
        }
    }

    // A square is SQUARE_SIZE px box defined by 2 triangles centered on the given 2D-point
    // To be drawn by the bitmap shader, each point that needs a square drawn around it requires
    // 4 vertices (8 coordinates), 8 texture coordinates, 3 rgb colour values, and 6 indices.
    // This class stores only the vertices
    public static class Square {
        public static final float SQUARE_SIZE_PX = 100f;
        public static final float DISTANCE_FROM_CENTER = SQUARE_SIZE_PX / 2.0f;
        public static final int POSITION_DIM = 8;
        public static final int TOTAL_DIM = POSITION_DIM;
        public static final int POSITION_SIZE = POSITION_DIM * FLOAT_SIZE;
        public static final int TOTAL_SIZE = POSITION_SIZE;
        public float[] data = new float[TOTAL_DIM];

        public Square(float x, float y) {
            // Vertex 1
            data[0] = x - DISTANCE_FROM_CENTER;
            data[1] = y - DISTANCE_FROM_CENTER;
            // Vertex 2
            data[2] = x + DISTANCE_FROM_CENTER;
            data[3] = y - DISTANCE_FROM_CENTER;
            // Vertex 3
            data[4] = x + DISTANCE_FROM_CENTER;
            data[5] = y + DISTANCE_FROM_CENTER;
            // Vertex 4
            data[6] = x - DISTANCE_FROM_CENTER;
            data[7] = y + DISTANCE_FROM_CENTER;
        }

        // Create a Square from a Vertex
        public Square(Vertex v) {
            this(v.data[0], v.data[1]);
        }
        // Create a Square from a DrawPoint
        public Square(DrawPoint drawPoint) {
            this(drawPoint.point.x, drawPoint.point.y);
        }
        // Create a Square from a PointF
    }
}