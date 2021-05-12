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
package dev.chromeos.lowlatencystylusdemo.cpu;


import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.SparseArray;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import com.google.chromeos.lowlatencystylus.BatchedMotionEvent;
import com.google.chromeos.lowlatencystylus.InkDelegate;
import java.util.ArrayList;
import java.util.List;

/** A Gesture represents a set of MotionEvents between ACTION_DOWN to ACTION_UP. */
public class Gesture {
    // List of received events
    private final List<PaintedEvent> paintedEvents = new ArrayList<>();

    /**
     * Adds a MotionEvent to Gesture. The MotionEvent will be copied, therefore {@code clear()} or
     * {@code clearButRetainLast()} should be explicitly called to recycle them.
     *
     * @param event added MotionEvent
     */
    public void add(MotionEvent event, Paint paint) {
        paintedEvents.add(new PaintedEvent(MotionEvent.obtain(event), paint));
    }

    /** Clears all the MotionEvents. It calls {@code recycle()} of all the MotionEvents. */
    public void clear() {
        for (PaintedEvent gesture : paintedEvents) {
            gesture.event.recycle();
        }
        paintedEvents.clear();
    }

    /** Clears all the MotionEvents except the last one. */
    public void clearButRetainLast() {
        if (paintedEvents.isEmpty()) {
            return;
        }
        PaintedEvent last = paintedEvents.get(paintedEvents.size() - 1);
        for (int i = 0; i < paintedEvents.size() - 1; i++) {
            paintedEvents.get(i).event.recycle();
        }
        paintedEvents.clear();
        paintedEvents.add(last);
    }

    /**
     * Draws the lines defined by the consecutive MotionEvents.
     *
     * @param canvas the lines are drawn to
     * @return the damage {@link Rect}
     */
    @NonNull
    public Rect draw(@NonNull Canvas canvas) {
        Rect damageRect = new Rect();
        SparseArray<Path> paths = new SparseArray<>();
        for (PaintedEvent gesture : paintedEvents) {
            for (int i = 0; i < gesture.event.getPointerCount(); i++) {
                final int id = gesture.event.getPointerId(i);
                Path path = paths.get(id);
                if (path == null) {
                    path = new Path();
                    paths.put(id, path);
                }
                for (BatchedMotionEvent motionEvent : BatchedMotionEvent.iterate(gesture.event)) {
                    float x = motionEvent.getCoords()[i].x;
                    float y = motionEvent.getCoords()[i].y;
                    if (path.isEmpty()) {
                        path.moveTo(x, y);
                    } else {
                        path.lineTo(x, y);
                    }
                    InkDelegate.unionRect(damageRect, x, y, gesture.paint.getStrokeWidth());
                }
            }
        }
        for (int i = 0; i < paths.size(); i++) {
            if (paintedEvents.size() > i) {
                canvas.drawPath(paths.valueAt(i), paintedEvents.get(i).paint);
            }
        }
        paths.clear();
        return damageRect;
    }

    /**
     * Draws the lines defined by {@code event}. Same as creating a new {@link Gesture} object with
     * {@code event} and calling the {@code draw()} method.
     *
     * @param canvas the lines are drawn to
     * @param paint style of the lines
     * @param event predicted MotionEvent
     * @return the damage {@link Rect}
     */
    @NonNull
    public static Rect drawMotionEvent(
            @NonNull Canvas canvas, @NonNull Paint paint, @NonNull MotionEvent event) {
        Gesture gesture = new Gesture();
        // As the gesture object is temporary, do not obtain() the event.
        gesture.paintedEvents.add(new PaintedEvent(event, paint));
        return gesture.draw(canvas);
    }

    // Convenience class to hold a motion event and a paint
    private static class PaintedEvent {
        MotionEvent event;
        Paint paint;

        public PaintedEvent(MotionEvent event, Paint paint) {
            this.event = event;
            this.paint = paint;
        }
    }
}