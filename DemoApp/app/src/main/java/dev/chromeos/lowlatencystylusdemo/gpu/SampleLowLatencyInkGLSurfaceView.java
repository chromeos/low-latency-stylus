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

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.google.chromeos.lowlatencystylus.BatchedMotionEvent;
import com.google.chromeos.lowlatencystylus.gpu.GLInkRenderer;
import com.google.chromeos.lowlatencystylus.gpu.GLInkSurfaceView;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * A low-latency SurfaceView that will manage input events and queue them appropriately to
 * a custom GLInkRenderer.
 */
public class SampleLowLatencyInkGLSurfaceView extends GLInkSurfaceView {
    // Ink colors described in rgb float arrays, values range 0.0 -> 1.0
    public static final float[] INK_COLOR_RED = { 1.0f, 0.0f, 0.0f };
    public static final float[] INK_COLOR_GREEN = { 0.0f, 1.0f, 0.0f };
    public static final float[] INK_COLOR_BLUE = { 0.0f, 0.0f, 1.0f };
    public static final float[] INK_COLOR_BLACK = { 0.0f, 0.0f, 0.0f };

    // Currently selected brush color
    public float[] mBrushColor = INK_COLOR_BLACK;

    private final SampleInkRenderer mRenderer;

    public SampleLowLatencyInkGLSurfaceView(Context context) {
        super(context);
        mRenderer = new SampleInkRenderer();

        // Set the renderer for drawing on the low latency ink GLSurfaceView
        setRenderer(mRenderer);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    /**
     * Handle stylus, mouse, and finger events
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        PointF point = new PointF(event.getX(), event.getY());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                queueEvent(() -> mRenderer.beginStroke(point));
                break;
            case MotionEvent.ACTION_UP:
                queueEvent(() -> mRenderer.endStroke(point));
                break;
            case MotionEvent.ACTION_MOVE:
                List<PointF> points = new ArrayList<>();
                for (BatchedMotionEvent ev : BatchedMotionEvent.iterate(event)) {
                    PointF historicalPoint = new PointF(ev.getCoords()[0].x, ev.getCoords()[0].y);
                    points.add(historicalPoint);
                }
                points.add(point);
                queueEvent(() -> mRenderer.addStrokes(points));
                break;
            default:
                break;
        }
        // Pass the MotionEvent down to the library to draw on the canvas, and drive the prediction
        // engine. This will also request unbuffered input in order to deliver input events as fast
        // as possible. If you are not passing MotionEvents directly from the system, be sure to
        // call View.requestUnbufferedDispatch manually for each ACTION_DOWN event. See:
        // https://developer.android.com/reference/android/view/View#requestUnbufferedDispatch(android.view.MotionEvent)
        onTouch(event);
        return true;
    }

    public void clear() {
        queueEvent(mRenderer::clear);
        requestRender();
    }

    /**
     * GLInkRenderer that draws to the GLSurfaceView
     */
    private class SampleInkRenderer extends GLInkRenderer {
        private final float[] mModelMatrix;
        private final float[] mMVPMatrix;
        private PointF mLastInkPoint;
        private PointF mLastPredictedPoint;

        // The brush shader
        private BrushShader mBrushShader;
        // Keeps track of the current damaged area
        private final InkGLSurfaceScissor mScissor = new InkGLSurfaceScissor();

        SampleInkRenderer() {
            super();
            mMVPMatrix = new float[16];
            mModelMatrix = new float[16];
            // For this demo, use the identity matrix
            Matrix.setIdentityM(mModelMatrix, 0);
        }

        @Override
        public void clear() {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if (mBrushShader != null) {
                mBrushShader.clear();
            }
            // For this demo, use the identity matrix
            Matrix.setIdentityM(mModelMatrix, 0);
        }

        /**
         * Adds predicted events to the brush shader as well as calculating the correct damage
         * region {@link Rect} for the given {@code predictedEvent} before drawing the frame.
         *
         * The returned {@link Rect} will be passed to a glScissor call before
         * {@link this.onDraw} is called.
         *
         * The predictedEvent contains the predicted position of each pointer currently on screen. The
         * predictedEvent is also likely to contain predicted historical position of each pointer. The
         * {@code predictedEvent} can be null when predictTargetMs is zero or there is not enough
         * samples received to predict.
         *
         * @param predictedEvent predicted {@link MotionEvent}. Can be null if no prediction events
         *                       are available.
         * @return the damage {@link Rect}
         */
        @NonNull
        @Override
        public Rect beforeDraw(GL10 unused, MotionEvent predictedEvent) {
            if (predictedEvent != null && mLastInkPoint != null) {
                // Include the last real and last predicted point in the damage rect
                addToScissor(mLastPredictedPoint);
                addToScissor(mLastInkPoint);

                // Start draw prediction from the last real point
                PointF lastPoint = mLastInkPoint;

                // If there are batched historical predictions, include those
                for (BatchedMotionEvent ev : BatchedMotionEvent.iterate(predictedEvent)) {
                    PointF batchedPoint = new PointF(ev.getCoords()[0].x, ev.getCoords()[0].y);

                    // Lines will be drawn with GL_LINES so add lines in pairs of points
                    addPredictedPoint(lastPoint, false);
                    addPredictedPoint(batchedPoint);

                    lastPoint = batchedPoint;
                }

                // Add the predicted point
                PointF predictedPoint = new PointF(predictedEvent.getX(), predictedEvent.getY());
                addPredictedPoint(lastPoint, false);
                addPredictedPoint(predictedPoint);

                // Keep track of the last predicted point for calculating damage
                mLastPredictedPoint = predictedPoint;
            }
            return new Rect(mScissor.getScissorBox());
        }

        /**
         * Add a single predicted point to the brush shader and update the scissor
         */
        private void addPredictedPoint(PointF point) {
            addPredictedPoint(point, true);
        }
        /**
         * Add a single predicted point to the brush shader and optionally update the scissor
         */
        private void addPredictedPoint(PointF point, Boolean shouldAddToScissor) {
            mBrushShader.addPredictionVertex(getVertexFromPoint(point));
            if (shouldAddToScissor) {
                addToScissor(point);
            }
        }

        /**
         * Draw for current frame with given {@code predictedEvent} if available.
         *
         * The predictedEvent contains the predicted position of each pointer currently on screen.
         * This was already added to the BrushShader in beforeDraw so ignore it here.
         *
         * @param predictedEvent predicted {@link MotionEvent}. Unused.
         */
        @Override
        public void onDraw(GL10 unused, MotionEvent predictedEvent) {
            updateMVPMatrix();
            mBrushShader.draw(mMVPMatrix);
            mScissor.reset();
        }

        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            super.onSurfaceCreated(unused, config);
            mBrushShader = new BrushShader();
        }

        @Override
        public void onSurfaceChanged(GL10 unused, int width, int height) {
            mBrushShader.clear();
            super.onSurfaceChanged(unused, width, height);
        }

        void beginStroke(PointF point) {
            addToScissor(point);
            mLastInkPoint = point;
        }

        void addStrokes(List<PointF> points) {
            if (mLastInkPoint == null) {
                return;
            }
            // Make sure scissor includes previously predicted points
            addToScissor(mLastPredictedPoint);

            // Add new points, starting from the last drawn point
            addToScissor(mLastInkPoint);
            for (PointF p : points) {
                addToScissor(p);
                addVertex(mLastInkPoint);
                addVertex(p);
                mLastInkPoint = p;
            }
        }

        void endStroke(PointF point) {
            if (mLastInkPoint == null) {
                return;
            }
            // Make sure scissor includes the previously predicted point
            addToScissor(mLastPredictedPoint);
            // Add the line from the previous point to the last point of the gesture
            addToScissor(mLastInkPoint);
            addToScissor(point);
            addVertex(mLastInkPoint);
            addVertex(point);

            mLastInkPoint = null;
        }

        private void addToScissor(PointF point) {
            if (point == null) { return; }
            PointF p = applyMatrixToPoint(getViewMatrix(), point);
            mScissor.addPoint(p.x, p.y);
        }

        private void addVertex(PointF point) {
            mBrushShader.addVertex(getVertexFromPoint(point));
        }

        /**
         * Convert an x,y PointF into a {@link BrushShader.Vertex} using the current brush color
         *
         * @param point The point to convert to a Vertex
         * @return The Vertex with x,y,r,g,b info set
         */
        private BrushShader.Vertex getVertexFromPoint(PointF point) {
            // Apply the inverse transform to the input point, because the canvas is shifted.
            float[] inverseView = new float[16];
            Matrix.invertM(inverseView, 0, mModelMatrix, 0);
            PointF p = applyMatrixToPoint(inverseView, point);

            // Create the Vertex
            BrushShader.Vertex vertex = new BrushShader.Vertex(p.x, p.y);
            // Set the color to be the current paint color
            vertex.setColor(mBrushColor[0], mBrushColor[1], mBrushColor[2]);

            return vertex;
        }

        private void updateMVPMatrix() {
            float[] tmpM = new float[16];
            Matrix.multiplyMM(tmpM, 0, getProjectionMatrix(), 0, getViewMatrix(), 0);
            Matrix.multiplyMM(mMVPMatrix, 0, tmpM, 0, mModelMatrix, 0);
        }

        private PointF applyMatrixToPoint(float[] matrix, PointF point) {
            float[] pointV = new float[] {point.x, point.y, 0, 1};
            float[] result = new float[4];
            Matrix.multiplyMV(result, 0, matrix, 0, pointV, 0);
            return new PointF(result[0], result[1]);
        }
    }
}