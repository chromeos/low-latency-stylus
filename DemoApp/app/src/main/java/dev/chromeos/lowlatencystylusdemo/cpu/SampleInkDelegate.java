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
import android.graphics.Rect;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import com.google.chromeos.lowlatencystylus.InkDelegate;

/** Sample implementation of {@link InkDelegate}.
 *
 * An {@link InkDelegate} handles drawing uncommitted lines and predicted lines on the overlay view,
 * and aids in calculating the damage rectangle for drawing events.
 *
 */
public class SampleInkDelegate extends InkDelegate {
    private Paint inkPaint;
    /**
     * This Gesture will retain all the MotionEvents between the last ACTION_DOWN to the next
     * ACTION_UP. It does not contain any predicted events so when the next real MotionEvent is
     * received, any previous predictions will not be drawn.
     */
    @NonNull
    Gesture gesture = new Gesture();

    public SampleInkDelegate(Paint paint) {
        this.inkPaint = paint;
    }
    public void setInkPaint(Paint paint) {
        inkPaint = paint;
    }

    @Override
    public void onTouch(@NonNull MotionEvent event) {
        // If this is a new gesture, clear all the previous MotionEvents
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            gesture.clear();
        }

        // Add the new event
        gesture.add(event, inkPaint);
    }

    @NonNull
    @Override
    public Rect onDraw(@NonNull Canvas canvas) {
        // Draw the saved motion events and calculate the damage rectangle
        Rect damaged = gesture.draw(canvas);

        // In case a drawing gesture is not complete, retain the last MotionEvent in order to know
        // where to start the next line.
        gesture.clearButRetainLast();
        return damaged;
    }

    @NonNull
    @Override
    public Rect onDrawPrediction(@NonNull Canvas canvas, @NonNull MotionEvent predictedEvent) {
        // Draws the predicted motion events and calculate the predicted damage rectangle
        return Gesture.drawMotionEvent(canvas, inkPaint, predictedEvent);
    }

    public void clear() {
        gesture.clear();
    }
}