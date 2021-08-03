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

package dev.chromeos.lowlatencystylusdemo.gpu.gpu_compare;

import static dev.chromeos.lowlatencystylusdemo.gpu.SampleGLInkSurfaceView.INK_COLOR_BLACK;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;
import android.view.Surface;

import com.google.chromeos.lowlatencystylus.BatchedMotionEvent;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

import dev.chromeos.lowlatencystylusdemo.R;
import dev.chromeos.lowlatencystylusdemo.gpu.BrushShader;
import dev.chromeos.lowlatencystylusdemo.gpu.DrawPoint;
import dev.chromeos.lowlatencystylusdemo.gpu.DrawPoints;


public class RegularCanvasSurfaceView extends GLSurfaceView {
    // Currently selected brush color
    public float[] inkStrokeColor = INK_COLOR_BLACK;

    private final RegularInkRenderer mRenderer;

    public RegularCanvasSurfaceView(Context context) {
        super(context);
        this.setEGLContextClientVersion(2);
        setEGLConfigChooser(new regularCanvasConfigChooser());
        mRenderer = new RegularInkRenderer();
        setRenderer(mRenderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
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

        requestRender();
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
        requestRender();
    }

    /**
     * GLInkRenderer that draws to the GLSurfaceView
     */
    private class RegularInkRenderer implements GLSurfaceView.Renderer {
        private final float[] mModelMatrix;
        private final float[] mProjectionMatrix;
        private final float[] mViewMatrix;
        private final float[] mMVPMatrix;
        private PointF mLastInkPoint;

        private int mWidth;
        private int mHeight;

        // The brush shader
        private BrushShader mBrushShader;
        // Spray paint bitmap
        private final Bitmap mSprayPaintBitmap;

        RegularInkRenderer() {
            super();
            mMVPMatrix = new float[16];
            mModelMatrix = new float[16];
            mProjectionMatrix = new float[16];
            mViewMatrix = new float[16];
            // For this demo, use the identity matrix
            Matrix.setIdentityM(mModelMatrix, 0);
            Matrix.setIdentityM(mProjectionMatrix, 0);
            Matrix.setIdentityM(mViewMatrix, 0);
            Matrix.setIdentityM(mMVPMatrix, 0);

            // Load spray paint brush bitmap
            mSprayPaintBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.spray_brush);
        }

        public void clear() {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if (mBrushShader != null) {
                mBrushShader.clear();
            }
        }

        public void enableSprayPaint(boolean enableSprayPaint) {
            if (mBrushShader != null) {
                mBrushShader.enableSprayPaint(enableSprayPaint);
            }
        }

        void beginStroke(PointF point) {
            addVertex(point);
            mLastInkPoint = point;
        }

        void addStrokes(List<PointF> points) {
            for (PointF p : points) {
                // Some devices / styli can generate a series of nearly identical points. This can
                // smear bitmap brushes and cause unnecessary slow-downs. Do not draw a point if it
                // is the same or nearly the same as the previous one.
                if (arePointsClose(mLastInkPoint, p)) {
                    continue;
                }
                addVertex(p);
                mLastInkPoint = p;
            }
        }

        void endStroke(PointF point) {
            addVertex(point);
            mLastInkPoint = point;
            // Tell the brush shader this was the end of a stroke
            mBrushShader.endLine();
        }

        private void addVertex(PointF point) {
            DrawPoint drawPoint = getDrawPointFromPoint(point);
            mBrushShader.addDrawPoint(drawPoint);
        }

        // Return true if sequential points are almost identical (less than 1px X and Y away)
        private boolean arePointsClose(PointF p1, PointF p2) {
            float diffX = Math.abs(p1.x - p2.x);
            float diffY = Math.abs(p1.y - p2.y);
            // We don't need the real difference (pythagoras), just if they're more than 1px apart
            return !(diffX + diffY > 2f);
        }

        private DrawPoint getDrawPointFromPoint(PointF point) {
            // Apply the inverse transform to the input point, because the canvas is shifted.
            float[] inverseView = new float[16];
            Matrix.invertM(inverseView, 0, mModelMatrix, 0);
            PointF p = applyMatrixToPoint(inverseView, point);
            return new DrawPoint(p, inkStrokeColor[0], inkStrokeColor[1], inkStrokeColor[2]);
        }

        private void updateMVPMatrix() {
            updateViewAndProjectionMatrix(mWidth, mHeight);
            float[] tmpM = new float[16];
            Matrix.multiplyMM(tmpM, 0, mProjectionMatrix, 0, mViewMatrix, 0);
            Matrix.multiplyMM(mMVPMatrix, 0, tmpM, 0, mModelMatrix, 0);
        }

        private PointF applyMatrixToPoint(float[] matrix, PointF point) {
            float[] pointV = new float[] {point.x, point.y, 0, 1};
            float[] result = new float[4];
            Matrix.multiplyMV(result, 0, matrix, 0, pointV, 0);
            return new PointF(result[0], result[1]);
        }

        @Override
        public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
            mBrushShader = new BrushShader();
            mBrushShader.initSprayPaintTexture(gl10, mSprayPaintBitmap);
            mSprayPaintBitmap.recycle();
        }

        @Override
        public void onSurfaceChanged(GL10 gl10, int width, int height) {
            mWidth = width;
            mHeight = height;
            mBrushShader.clear();
        }

        @Override
        public void onDrawFrame(GL10 gl10) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            updateMVPMatrix();
            mBrushShader.draw(mMVPMatrix);
            GLES20.glFlush();
        }

        private void updateViewAndProjectionMatrix(int width, int height) {
            int rotation = getDisplay().getRotation();

            // Simplfy to just 2 cases
            if (rotation == Surface.ROTATION_180) {
                rotation = Surface.ROTATION_0;
            }
            if (rotation == Surface.ROTATION_270) {
                rotation = Surface.ROTATION_90;
            }

            if (rotation == Surface.ROTATION_0) {
                Matrix.orthoM(
                        mProjectionMatrix, /* result */
                        0 /* offset */,
                        0 /* left */,
                        width /* right */,
                        0 /* bottom */,
                        height /* top */,
                        -1 /* near */,
                        1 /* far */);
                // (0, 0) is at top left for touch events, left bottom for gl surface.
                Matrix.setIdentityM(mViewMatrix, 0);
                Matrix.translateM(
                        mViewMatrix,
                        0 /* offset */,
                        0 /* x */,
                        height /* y */,
                        0 /* z */);
                Matrix.rotateM(
                        mViewMatrix,
                        0 /* offset */,
                        180 /* angle */,
                        1 /* x */,
                        0 /* y */,
                        0 /* z */);
            } else if (rotation == Surface.ROTATION_90) {
                Matrix.orthoM(
                        mProjectionMatrix, /* result */
                        0 /* offset */,
                        0 /* left */,
                        height /* right */,
                        0 /* bottom */,
                        width /* top */,
                        -1 /* near */,
                        1 /* far */);
                Matrix.setIdentityM(mViewMatrix, 0);
                Matrix.translateM(
                        mRenderer.mViewMatrix,
                        0 /* offset */,
                        height /* x */,
                        width /* y */,
                        0 /* z */);
                Matrix.rotateM(
                        mViewMatrix,
                        0 /* offset */,
                        180 /* angle */,
                        0 /* x */,
                        1 /* y */,
                        0 /* z */);
                Matrix.rotateM(
                        mViewMatrix,
                        0 /* offset */,
                        -90 /* angle */,
                        0 /* x */,
                        0 /* y */,
                        1 /* z */);
            }
        }
    }



    static int eglGetConfigAttrib(
            EGL10 egl,
            javax.microedition.khronos.egl.EGLDisplay display,
            javax.microedition.khronos.egl.EGLConfig config,
            int attribute) {
        int[] value = new int[1];
        if (!egl.eglGetConfigAttrib(display, config, attribute, value)) {
            throw new RuntimeException(
                    String.format(
                            "eglGetConfigAttrib(attribute=0x%X) failed: EGL error 0x%X",
                            attribute, egl.eglGetError()));
        }
        return value[0];
    }

    static int eglQueryContext(
            android.opengl.EGLDisplay display, android.opengl.EGLContext context, int attribute) {
        int[] value = new int[1];
        if (!EGL14.eglQueryContext(display, context, attribute, value, 0)) {
            throw new RuntimeException(
                    String.format(
                            "eglQueryContext(attribute=0x%X) failed: EGL error 0x%X",
                            attribute, EGL14.eglGetError()));
        }
        return value[0];
    }

    private static class regularCanvasConfigChooser implements GLSurfaceView.EGLConfigChooser {
        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            EGLConfig config = null;
            int[] configSpec =
                    new int[]{
                            EGL10.EGL_BUFFER_SIZE, 24,
                            EGL10.EGL_RED_SIZE, 8,
                            EGL10.EGL_GREEN_SIZE, 8,
                            EGL10.EGL_BLUE_SIZE, 8,
                            EGL10.EGL_ALPHA_SIZE, 0,
                            EGL10.EGL_DEPTH_SIZE, 0,
                            EGL10.EGL_STENCIL_SIZE, 0,
                            EGL10.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                            EGL10.EGL_NONE
                    };
            config = chooseConfigBySpec(egl, display, configSpec);

            if (config == null) {
                throw new IllegalArgumentException("eglChooseConfig failed");
            }
            return config;
        }

        public EGLConfig chooseConfigBySpec(EGL10 egl, EGLDisplay display, int[] configSpec) {
            int[] numConfig = new int[1];
            if (!egl.eglChooseConfig(display, configSpec, null, 0, numConfig)) {
                return null;
            }

            int numConfigs = numConfig[0];

            if (numConfigs <= 0) {
                return null;
            }

            EGLConfig[] configs = new EGLConfig[numConfigs];
            if (!egl.eglChooseConfig(display, configSpec, configs, numConfigs, numConfig)) {
                throw new IllegalArgumentException("eglChooseConfig#2 failed");
            }
            return configs[0];
        }
    }
}