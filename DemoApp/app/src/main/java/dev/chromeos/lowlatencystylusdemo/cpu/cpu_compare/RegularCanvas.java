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

package dev.chromeos.lowlatencystylusdemo.cpu.cpu_compare;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import java.util.ArrayList;

/**
 * A simple implementation of a drawing canvas without low-latency.
 *
 * Like {@link dev.chromeos.lowlatencystylusdemo.cpu.SampleInkView}, this records all drawing
 * gestures in a list, along with the Paint selected at the time of drawing.
 */
public class RegularCanvas extends View {
    private Paint currentPaint;
    private final Path currentPath;
    private final ArrayList<PaintedPath> previousPaths;

    public RegularCanvas(Context context, Paint paint) {
        super(context);
        currentPaint = paint;
        currentPath = new Path();
        previousPaths = new ArrayList<>();
    }

    public void setCurrentPaint(Paint paint) { currentPaint = paint; }

    @Override
    public void onDraw(@NonNull Canvas canvas) {
        // Draw finished paths
        previousPaths.forEach(paintedPath -> {
            canvas.drawPath(paintedPath.path, paintedPath.paint);
        });

        // Draw the current (unfinished) path
        canvas.drawPath(currentPath, currentPaint);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent ev) {

        // Respond to down, move and up events
        switch (ev.getAction()) {
            // On down, move path start to event position
            case MotionEvent.ACTION_DOWN:
                currentPath.moveTo(ev.getX(), ev.getY());
                break;

            // Draw a line event position moves
            case MotionEvent.ACTION_MOVE:
                currentPath.lineTo(ev.getX(), ev.getY());
                break;

            // When stroke is finished, save path and color to previousPaths
            case MotionEvent.ACTION_UP:
                currentPath.lineTo(ev.getX(), ev.getY());
                previousPaths.add(new PaintedPath(new Path(currentPath), currentPaint));
                currentPath.reset();
                break;

            default:
                return false;
        }
        //redraw
        invalidate();
        return true;
    }

    public void clear() {
        previousPaths.clear();
        currentPath.reset();
        this.invalidate();
    }

    // Convenience class to hold both a Path and a Paint
    private static class PaintedPath {
        Path path;
        Paint paint;

        public PaintedPath(Path path, Paint paint) {
            this.path = path;
            this.paint = paint;
        }
    }
}