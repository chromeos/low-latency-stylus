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

import android.graphics.Rect;

/**
 * Used to calculate the damaged area during an ink stroke. This allows for incremental rendering
 * and lower latency. Will be used as a glScissor.
 */
public class InkGLSurfaceScissor {
    // Add a "border" to the render box to ensure all pixels are captured
    private static final int RENDER_BOX_OFFSET = ((int) BrushShader.BRUSH_SIZE) + 1;
    private Rect mScissorBox;
    private boolean mEmpty;

    public InkGLSurfaceScissor() {
        reset();
    }

    /**
     * Clear the damage area
     */
    public void reset() {
        mEmpty = true;
        mScissorBox = new Rect();
    }

    /**
     * Extend the current damage area to include the given point.
     */
    public void addPoint(float x, float y) {
        if (mEmpty) {
            mScissorBox.set((int) x, (int) y, (int) x, (int) y);
            mEmpty = false;
        } else {
            mScissorBox.union((int) x, (int) y);
        }
    }

    /**
     * Return the damage rectangle for use as a glScissor.
     *
     * @return Rect of damage area with a slightly larger "border" to guarantee correctness
     */
    Rect getScissorBox() {
        return new Rect(mScissorBox.left - RENDER_BOX_OFFSET, mScissorBox.top - RENDER_BOX_OFFSET,
                mScissorBox.right + RENDER_BOX_OFFSET, mScissorBox.bottom + RENDER_BOX_OFFSET);
    }
}