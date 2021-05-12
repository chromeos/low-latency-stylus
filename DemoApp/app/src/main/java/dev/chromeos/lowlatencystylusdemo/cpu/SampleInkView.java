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

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_UP;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import com.google.chromeos.lowlatencystylus.InkOverlayView;
import java.util.ArrayList;
import java.util.List;

/**
 * The main canvas that will show previous, committed lines.
 *
 * <p>Drawing gestures, including predicted ones, will be first drawn to an {@link InkOverlayView}.
 * After the styluses/fingers are lifted from the screen, the final lines will be drawn to this
 * view.
 */
public class SampleInkView extends View {
    private InkOverlayView inkOverlayView;

    /** The style of the drawn lines. */
    private Paint inkPaint;

    /** A list of previously committed gestures. In this sample, retain them all. */
    @NonNull
    List<Gesture> committedGestures = new ArrayList<>();
    /** The current, uncommitted gesture. I.e. the fingers/styluses are still on screen. */
    @NonNull
    Gesture activeGesture = new Gesture();

    public SampleInkView(Context context, Paint paint) {
        super(context);
        this.inkPaint = paint;
    }

    public void setInkOverlayView(InkOverlayView inkOverlayView) {
        this.inkOverlayView = inkOverlayView;
    }

    public void setInkPaint(Paint paint) {
        inkPaint = paint;
    }

    @Override
    public void onDraw(@NonNull Canvas canvas) {
        for (Gesture gesture : committedGestures) {
                gesture.draw(canvas);
        }
    }

    /**
     * Handle incoming touch events
     *
     * If ACTION_DOWN or ACTION_MOVE, pass the event to the InkOverlayView to be drawn on the
     * overlay.
     *
     * If ACTION_UP is received, in addition to passing it to the overlay, a drawing gesture has
     * been completed. Commit it to this InkView, clear the OverlayView, and get ready for the next
     * gesture.
     *
     * If ACTION_CANCEL, the system has cancelled the event, possibly due to palm rejection, erase
     * the cancelled gesture from the overlay and prepare for the next gesture.
     *
     * @param ev The incoming MotionEvent
     * @return true if the event was handled
     */
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent ev) {
        switch (ev.getActionMasked()) {
            // Palm rejection or event otherwise cancelled by the system, erase from overlay
            case ACTION_CANCEL:
                activeGesture.clear();
                inkOverlayView.clear();
                break;

            // A drawing gesture has finished.
            case ACTION_UP:
                // Commit the event to this canvas
                activeGesture.add(ev, inkPaint);
                committedGestures.add(activeGesture);

                // Prepare for the next gesture
                activeGesture = new Gesture();

                // Pass the ACTION_UP event to the overlay
                inkOverlayView.onTouch(ev);

                // Redraw this canvas with the newly committed event
                invalidate();

                // Clear the overlay
                inkOverlayView.clear();
                break;

            // A new drawing gesture or a continued line (ACTION_DOWN or ACTION_MOVE)
            default:
                // Add the point to the current gesture and pass it to the overlay to be drawn there
                activeGesture.add(ev, inkPaint);
                inkOverlayView.onTouch(ev);
                break;
        }
        return true;
    }

    public void clear() {
        // Clear the active and all recorded gestures
        for (Gesture gesture : committedGestures) {
            gesture.clear();
        }
        activeGesture.clear();

        // Request a re-draw
        this.invalidate();
    }
}