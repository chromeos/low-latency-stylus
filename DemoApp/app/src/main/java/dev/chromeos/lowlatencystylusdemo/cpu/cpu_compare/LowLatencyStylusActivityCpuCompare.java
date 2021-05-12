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

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.slider.Slider;
import com.google.chromeos.lowlatencystylus.InkOverlayView;

import dev.chromeos.lowlatencystylusdemo.R;
import dev.chromeos.lowlatencystylusdemo.cpu.SampleInkDelegate;
import dev.chromeos.lowlatencystylusdemo.cpu.SampleInkView;

public class LowLatencyStylusActivityCpuCompare extends AppCompatActivity {
    // Brush buttons
    private final int NUM_BRUSHES = 4;
    private final int BRUSH_BLACK = 0, BRUSH_RED = 1, BRUSH_GREEN = 2, BRUSH_BLUE = 3;
    private final ImageButton[] brushButtons = new ImageButton[NUM_BRUSHES];

    // Brush paints
    private static final float PAINT_STROKE_WIDTH = 3f;
    private final Paint PAINT_BLACK = createPaint(0xff000000);
    private final Paint PAINT_RED = createPaint(0xffff0000);
    private final Paint PAINT_GREEN = createPaint(0xff00ff00);
    private final Paint PAINT_BLUE = createPaint(0xff0000ff);
    private final Paint PAINT_DEFAULT = PAINT_BLACK;

    // The main low-latency canvas and the overlay
    private SampleInkView mBaseView;
    private SampleInkDelegate mInkDelegate;

    // The regular, not low-latency, canvas
    private RegularCanvas mRegularCanvas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Low Latency Stylus - CPU - Compare");

        setContentView(R.layout.compare);
        FrameLayout lowLatencyCanvas = findViewById(R.id.frameLowLatencyCanvas);
        FrameLayout regularCanvas = findViewById(R.id.frameRegularCanvas);

        // Enable the Up / Back button
        ActionBar ab = getSupportActionBar();
        if (ab != null) ab.setDisplayHomeAsUpEnabled(true);

        // The regular, not low-latency, canvas
        mRegularCanvas = new RegularCanvas(this, PAINT_DEFAULT);
        regularCanvas.addView(mRegularCanvas);

        // The base drawing canvas which shows committed lines.
        mBaseView = new SampleInkView(this, PAINT_DEFAULT);
        lowLatencyCanvas.addView(mBaseView);

        // Create an overlay view to be shown on top of the main canvas that will show uncommitted
        // lines with prediction. Create an InkDelegate in order to draw on the InkOverlayView.
        final InkOverlayView overlayView = new InkOverlayView(this);
        mInkDelegate = new SampleInkDelegate(PAINT_DEFAULT);
        overlayView.setInkDelegate(mInkDelegate);

        // Connect the overlay view to the base canvas
        mBaseView.setInkOverlayView(overlayView);

        // Set prediction target in ms. If not set, library defaults to 25ms. Set to 0 to disable.
        overlayView.setPredictionTarget(25);

        // Add overlay on top of the base canvas
        lowLatencyCanvas.addView(overlayView);

        // Set up brush buttons
        brushButtons[BRUSH_BLACK] = findViewById(R.id.button_black);
        brushButtons[BRUSH_RED] = findViewById(R.id.button_red);
        brushButtons[BRUSH_GREEN] = findViewById(R.id.button_green);
        brushButtons[BRUSH_BLUE] = findViewById(R.id.button_blue);
        for (int n = 0; n < NUM_BRUSHES; n++) {
            int selectedBrush = n;
            brushButtons[n].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    selectBrush(selectedBrush);
                }
            });
        }

        // Start with black brush selected
        selectBrush(BRUSH_BLACK);

        // Set up the prediction target slider
        Slider sliderPrediction = findViewById(R.id.slider_prediction);
        sliderPrediction.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                overlayView.setPredictionTarget(Math.round(value));
            }
        });
    }

    /**
     * Keyboard event handling
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            // Clear the surface when the space key is pressed
            mBaseView.clear();
            mInkDelegate.clear();
            mRegularCanvas.clear();
            return true;
        }

        // Did not handle key press
        return false;
    }

    private static Paint createPaint(int color) {
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(PAINT_STROKE_WIDTH);
        return paint;
    }

    /**
     * Set the brush color and highlight the selected brush in the button bar.
     *
     * @param selectedBrush The brush that has just been selected
     */
    private void selectBrush(int selectedBrush) {
        switch (selectedBrush) {
            case BRUSH_RED:
                mRegularCanvas.setCurrentPaint(PAINT_RED);
                mBaseView.setInkPaint(PAINT_RED);
                mInkDelegate.setInkPaint(PAINT_RED);
                brushButtons[BRUSH_BLACK].setBackgroundColor(Color.TRANSPARENT);
                brushButtons[BRUSH_RED].setBackground(ContextCompat.getDrawable(
                        this, R.drawable.selected_circle));
                brushButtons[BRUSH_GREEN].setBackgroundColor(Color.TRANSPARENT);
                brushButtons[BRUSH_BLUE].setBackgroundColor(Color.TRANSPARENT);
                break;

            case BRUSH_GREEN:
                mRegularCanvas.setCurrentPaint(PAINT_GREEN);
                mBaseView.setInkPaint(PAINT_GREEN);
                mInkDelegate.setInkPaint(PAINT_GREEN);
                brushButtons[BRUSH_BLACK].setBackgroundColor(Color.TRANSPARENT);
                brushButtons[BRUSH_RED].setBackgroundColor(Color.TRANSPARENT);
                brushButtons[BRUSH_GREEN].setBackground(ContextCompat.getDrawable(
                        this, R.drawable.selected_circle));
                brushButtons[BRUSH_BLUE].setBackgroundColor(Color.TRANSPARENT);
                break;

            case BRUSH_BLUE:
                mRegularCanvas.setCurrentPaint(PAINT_BLUE);
                mBaseView.setInkPaint(PAINT_BLUE);
                mInkDelegate.setInkPaint(PAINT_BLUE);
                brushButtons[BRUSH_BLACK].setBackgroundColor(Color.TRANSPARENT);
                brushButtons[BRUSH_RED].setBackgroundColor(Color.TRANSPARENT);
                brushButtons[BRUSH_GREEN].setBackgroundColor(Color.TRANSPARENT);
                brushButtons[BRUSH_BLUE].setBackground(ContextCompat.getDrawable(
                        this, R.drawable.selected_circle));
                break;

            case BRUSH_BLACK:
            default:
                mRegularCanvas.setCurrentPaint(PAINT_BLACK);
                mBaseView.setInkPaint(PAINT_BLACK);
                mInkDelegate.setInkPaint(PAINT_BLACK);
                brushButtons[BRUSH_BLACK].setBackground(ContextCompat.getDrawable(
                        this, R.drawable.selected_circle));
                brushButtons[BRUSH_RED].setBackgroundColor(Color.TRANSPARENT);
                brushButtons[BRUSH_GREEN].setBackgroundColor(Color.TRANSPARENT);
                brushButtons[BRUSH_BLUE].setBackgroundColor(Color.TRANSPARENT);
                break;
        }
    }
}