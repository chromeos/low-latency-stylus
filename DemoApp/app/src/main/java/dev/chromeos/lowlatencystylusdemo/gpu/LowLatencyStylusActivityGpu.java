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

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.slider.Slider;
import dev.chromeos.lowlatencystylusdemo.R;

/**
 * Example app demonstrating using a GLInkSurfaceView to render to a low latency surface
 */
public class LowLatencyStylusActivityGpu extends AppCompatActivity {
    // Brush buttons
    private final int NUM_BRUSH_COLORS = 4;
    private final int BRUSH_BLACK = 0, BRUSH_RED = 1, BRUSH_GREEN = 2, BRUSH_BLUE = 3;
    private final ImageButton[] brushColorButtons = new ImageButton[NUM_BRUSH_COLORS];
    private final int NUM_BRUSHES = 2;
    private final int BRUSH_NORMAL = 0, BRUSH_SPRAY = 1;
    private final ImageButton[] brushButtons = new ImageButton[NUM_BRUSHES];

    // The main drawing canvas
    private SampleGLInkSurfaceView mGLInkSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Low Latency Stylus - GPU Driven");

        setContentView(R.layout.canvas);
        FrameLayout mainCanvas = findViewById(R.id.frameMainCanvas);

        // Enable the Up / Back button
        ActionBar ab = getSupportActionBar();
        if (ab != null) ab.setDisplayHomeAsUpEnabled(true);

        // Create a low latency GLSurfaceView and add it to the main canvas
        mGLInkSurfaceView = new SampleGLInkSurfaceView(this);
        mainCanvas.addView(mGLInkSurfaceView);

        // Set up buttons
        brushColorButtons[BRUSH_BLACK] = findViewById(R.id.button_black);
        brushColorButtons[BRUSH_RED] = findViewById(R.id.button_red);
        brushColorButtons[BRUSH_GREEN] = findViewById(R.id.button_green);
        brushColorButtons[BRUSH_BLUE] = findViewById(R.id.button_blue);
        for (int n = 0; n < NUM_BRUSH_COLORS; n++) {
            int selectedBrush = n;
            brushColorButtons[n].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    selectBrushColor(selectedBrush);
                }
            });
        }
        // Start with black brush selected
        selectBrushColor(BRUSH_BLACK);

        brushButtons[BRUSH_NORMAL] = findViewById(R.id.button_brush_normal);
        brushButtons[BRUSH_SPRAY] = findViewById(R.id.button_brush_spray);
        for (int n = 0; n < NUM_BRUSHES; n++) {
            int selectedBrush = n;
            brushButtons[n].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    selectBrush(selectedBrush);
                }
            });
        }
        // Start with normal paint brush selected
        selectBrush(BRUSH_NORMAL);

        // Set prediction target in ms. If not set, library defaults to 25ms. Set to 0 to disable.
        mGLInkSurfaceView.setPredictionTargetMs(25);

        // Set up prediction slider
        Slider sliderPrediction = findViewById(R.id.slider_prediction);
        sliderPrediction.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                mGLInkSurfaceView.setPredictionTargetMs(Math.round(value));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGLInkSurfaceView.onResume();
    }

    /**
     * Keyboard event handling
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            // Clear the surface when the space key is pressed
            mGLInkSurfaceView.clear();
            return true;
        }

        // Did not handle key press
        return false;
    }

    /**
     * Set the brush color and highlight the selected brush color in the button bar.
     *
     * @param selectedBrush
     */
    private void selectBrushColor(int selectedBrush) {
        switch (selectedBrush) {
            case BRUSH_RED:
                mGLInkSurfaceView.mBrushColor = SampleGLInkSurfaceView.INK_COLOR_RED;
                brushColorButtons[BRUSH_BLACK].setBackgroundColor(Color.TRANSPARENT);
                brushColorButtons[BRUSH_RED].setBackground(ContextCompat.getDrawable(
                        this, R.drawable.selected_circle));
                brushColorButtons[BRUSH_GREEN].setBackgroundColor(Color.TRANSPARENT);
                brushColorButtons[BRUSH_BLUE].setBackgroundColor(Color.TRANSPARENT);
                break;

            case BRUSH_GREEN:
                mGLInkSurfaceView.mBrushColor = SampleGLInkSurfaceView.INK_COLOR_GREEN;
                brushColorButtons[BRUSH_BLACK].setBackgroundColor(Color.TRANSPARENT);
                brushColorButtons[BRUSH_RED].setBackgroundColor(Color.TRANSPARENT);
                brushColorButtons[BRUSH_GREEN].setBackground(ContextCompat.getDrawable(
                        this, R.drawable.selected_circle));
                brushColorButtons[BRUSH_BLUE].setBackgroundColor(Color.TRANSPARENT);
                break;

            case BRUSH_BLUE:
                mGLInkSurfaceView.mBrushColor = SampleGLInkSurfaceView.INK_COLOR_BLUE;
                brushColorButtons[BRUSH_BLACK].setBackgroundColor(Color.TRANSPARENT);
                brushColorButtons[BRUSH_RED].setBackgroundColor(Color.TRANSPARENT);
                brushColorButtons[BRUSH_GREEN].setBackgroundColor(Color.TRANSPARENT);
                brushColorButtons[BRUSH_BLUE].setBackground(ContextCompat.getDrawable(
                        this, R.drawable.selected_circle));
                break;

            case BRUSH_BLACK:
            default:
                mGLInkSurfaceView.mBrushColor = SampleGLInkSurfaceView.INK_COLOR_BLACK;
                brushColorButtons[BRUSH_BLACK].setBackground(ContextCompat.getDrawable(
                        this, R.drawable.selected_circle));
                brushColorButtons[BRUSH_RED].setBackgroundColor(Color.TRANSPARENT);
                brushColorButtons[BRUSH_GREEN].setBackgroundColor(Color.TRANSPARENT);
                brushColorButtons[BRUSH_BLUE].setBackgroundColor(Color.TRANSPARENT);
                break;
        }
    }

    /**
     * Set the brush type and highlight the selected brush in the button bar.
     *
     * @param selectedBrush
     */
    private void selectBrush(int selectedBrush) {
        switch (selectedBrush) {
            case BRUSH_SPRAY:
                mGLInkSurfaceView.enableSprayPaint(true);
                mGLInkSurfaceView.redrawAll();
                brushButtons[BRUSH_NORMAL].setBackground(ContextCompat.getDrawable(
                        this, R.drawable.brush_not_selected));
                brushButtons[BRUSH_SPRAY].setBackground(ContextCompat.getDrawable(
                        this, R.drawable.brush_selected));
                break;

            default:
            case BRUSH_NORMAL:
                mGLInkSurfaceView.enableSprayPaint(false);
                mGLInkSurfaceView.redrawAll();
                brushButtons[BRUSH_NORMAL].setBackground(ContextCompat.getDrawable(
                        this, R.drawable.brush_selected));
                brushButtons[BRUSH_SPRAY].setBackground(ContextCompat.getDrawable(
                        this, R.drawable.brush_not_selected));
                break;
        }
    }
}