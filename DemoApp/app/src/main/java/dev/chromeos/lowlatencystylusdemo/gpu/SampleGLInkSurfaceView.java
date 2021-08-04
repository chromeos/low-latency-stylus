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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.google.chromeos.lowlatencystylus.BatchedMotionEvent;
import com.google.chromeos.lowlatencystylus.gpu.GLInkRenderer;
import com.google.chromeos.lowlatencystylus.gpu.GLInkSurfaceView;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import dev.chromeos.lowlatencystylusdemo.R;

/**
 * A low-latency SurfaceView that will manage input events and queue them appropriately to
 * a custom GLInkRenderer.
 */
public class SampleGLInkSurfaceView extends GLInkSurfaceView {
    // Ink colors described in rgb float arrays, values range 0.0 -> 1.0
    public static final float[] INK_COLOR_RED = { 1.0f, 0.0f, 0.0f };
    public static final float[] INK_COLOR_GREEN = { 0.0f, 1.0f, 0.0f };
    public static final float[] INK_COLOR_BLUE = { 0.0f, 0.0f, 1.0f };
    public static final float[] INK_COLOR_BLACK = { 0.0f, 0.0f, 0.0f };

    // Currently selected brush color
    public float[] mBrushColor = INK_COLOR_BLACK;

    private final SampleInkRenderer mRenderer;

    public SampleGLInkSurfaceView(Context context) {
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

    public void enableSprayPaint(boolean enableSprayPaint) {
        mRenderer.enableSprayPaint(enableSprayPaint);
    }

    public void clear() {
        queueEvent(mRenderer::clear);
        requestRender();
    }

    public void redrawAll() {
        queueEvent(mRenderer::redrawAll);
        requestRender();
    }

    /**
     * GLInkRenderer that draws to the GLSurfaceView
     */
    private class SampleInkRenderer extends GLInkRenderer {
        private final float[] mModelMatrix;
        private final float[] mMVPMatrix;
        private PointF mLastInkPoint;

        // The brush shader
        private BrushShader mBrushShader;
        // Spray paint bitmap
        private final Bitmap mSprayPaintBitmap;

        // Keeps track of the current damaged area
        private final InkGLSurfaceScissor mScissor = new InkGLSurfaceScissor();
        private final InkGLSurfaceScissor mPredictionScissor = new InkGLSurfaceScissor();
        private Rect mPrevPredictionDamageRect;

        SampleInkRenderer() {
            super();
            mMVPMatrix = new float[16];
            mModelMatrix = new float[16];
            mPrevPredictionDamageRect = new Rect(0, 0, 0, 0);
            // For this demo, use the identity matrix
            Matrix.setIdentityM(mModelMatrix, 0);

            // Load spray paint brush bitmap
            mSprayPaintBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.spray_brush);
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

        public void enableSprayPaint(boolean enableSprayPaint) {
            if (mBrushShader != null) {
                mBrushShader.enableSprayPaint(enableSprayPaint);
            }
        }

        public void redrawAll() {
            if (mBrushShader != null) {
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT); // Clear previous strokes
                mScissor.addRect(new Rect(0, 0, getWidth(), getHeight()));
                executeDraw();
            }
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
            // Include the last drawn point in the damage rectangle
            addToScissor(mLastInkPoint);

            if (predictedEvent != null) {
                // If there are batched historical predictions, include those
                for (BatchedMotionEvent ev : BatchedMotionEvent.iterate(predictedEvent)) {
                    PointF batchedPoint = new PointF(ev.getCoords()[0].x, ev.getCoords()[0].y);
                    addPredictedPoint(batchedPoint);
                }

                // Add the predicted point
                PointF predictedPoint = new PointF(predictedEvent.getX(), predictedEvent.getY());
                addPredictedPoint(predictedPoint);
            }

            // Get the current prediction damage rect
            Rect newPredictionDamageRect = mPredictionScissor.getScissorBox();
            // Include the previous prediction area in this draw only to ensure old lines are overwritten
            addToScissor(mPrevPredictionDamageRect, true);
            // Save the current prediction damage rect for the next draw
            mPrevPredictionDamageRect = newPredictionDamageRect;

            // Return combined nre prediction damage + previous prediction damage
            // Note: prediction damage will accumulate in mPredictionScissor during a stroke. This
            // ensure that predictions are correctly cleared. mPredictionScissor is reset at the end
            // of a stroke
            return mPredictionScissor.getScissorBox();
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
           mBrushShader.addPredictionDrawPoint(getDrawPointFromPoint(point));
           if (shouldAddToScissor) {
               addToScissor(point, true);
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
            executeDraw();
        }

        private void executeDraw() {
            updateMVPMatrix();
            mBrushShader.draw(mMVPMatrix);
            mScissor.reset();
        }

        @Override
        public void onSurfaceCreated(GL10 gl10, EGLConfig config) {
            super.onSurfaceCreated(gl10, config);
            mBrushShader = new BrushShader();
            // Initialize bitmap based shaders
            mBrushShader.initSprayPaintTexture(gl10, mSprayPaintBitmap);
            // Once texture is initialized, recycle the Bitmap memory
            mSprayPaintBitmap.recycle();
        }

        @Override
        public void onSurfaceChanged(GL10 unused, int width, int height) {
            mBrushShader.clear();
            super.onSurfaceChanged(unused, width, height);
        }

        void beginStroke(PointF point) {
            addToScissor(point);
            mLastInkPoint = point;
            addVertex(point);
        }

        void addStrokes(List<PointF> points) {
            // Make sure scissor includes previous prediction area
//            addToScissor(mPrevPredictionDamageRect);

            // Add new points
            for (PointF p : points) {
                // Some devices / styli can generate a series of nearly identical points. This can
                // smear bitmap brushes and cause unnecessary slow-downs. Do not draw a point if it
                // is the same or nearly the same as the previous one.
                if (arePointsClose(mLastInkPoint, p)) {
                    continue;
                }
                addToScissor(p);
                addVertex(p);
                mLastInkPoint = p;
            }
        }

        void endStroke(PointF point) {
            // Make sure scissor includes the previous prediction areas
//            addToScissor(mPrevPredictionDamageRect);
            // Add point
            addToScissor(point);
            addVertex(point);
            mLastInkPoint = point;

            // Tell the brush shader this was the end of a stroke
            mBrushShader.endLine();

            // Clear prediction scissor which has been accumulating during the stroke to ensure
            // all predicted strokes were cleared
            mPredictionScissor.reset();
        }

        private void addToScissor(PointF point) {
            addToScissor(point, false);
        }
        private void addToScissor(PointF point, boolean isPredictionPoint) {
            if (point == null) { return; }
            PointF p = applyMatrixToPoint(getViewMatrix(), point);
            if (isPredictionPoint) {
                mPredictionScissor.addPoint(p.x, p.y);
            } else {
                mScissor.addPoint(p.x, p.y);
            }
        }

        private void addToScissor(Rect rectToAdd) {
            addToScissor(rectToAdd, false);
        }
        private void addToScissor(Rect rectToAdd, boolean isPredictionRect) {
            if (rectToAdd == null) { return; }
            if (!rectToAdd.isEmpty()) {
                if (isPredictionRect) {
                    mPredictionScissor.addRect(rectToAdd);
                } else {
                    mScissor.addRect(rectToAdd);
                }
            }
        }

        // Add the point to the brush shader's draw list
        private void addVertex(PointF point) {
            mBrushShader.addDrawPoint(getDrawPointFromPoint(point));
        }

        // Return true if sequential points are almost identical (less than 1px X and Y away)
        private boolean arePointsClose(PointF p1, PointF p2) {
            float diffX = Math.abs(p1.x - p2.x);
            float diffY = Math.abs(p1.y - p2.y);
            // We don't need the real difference (pythagoras), just if they're more than 1px apart
            return (diffX + diffY < 2f);
        }

        /**
         * Convert an x,y PointF into a {@link DrawPoint} using the current brush color
         *
         * @param point The point to convert to a Vertex
         * @return The DrawPoint with x,y,r,g,b info set
         */
        private DrawPoint getDrawPointFromPoint(PointF point) {
            // Apply the inverse transform to the input point, because the canvas is shifted.
            float[] inverseView = new float[16];
            Matrix.invertM(inverseView, 0, mModelMatrix, 0);
            PointF p = applyMatrixToPoint(inverseView, point);

            // Create the DrawPoint
            DrawPoint drawPoint = new DrawPoint(p, mBrushColor[0], mBrushColor[1], mBrushColor[2]);

            return drawPoint;
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