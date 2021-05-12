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

package dev.chromeos.lowlatencystylusdemo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import dev.chromeos.lowlatencystylusdemo.cpu.LowLatencyStylusActivityCpu;
import dev.chromeos.lowlatencystylusdemo.cpu.cpu_compare.LowLatencyStylusActivityCpuCompare;
import dev.chromeos.lowlatencystylusdemo.gpu.LowLatencyStylusActivityGpu;
import dev.chromeos.lowlatencystylusdemo.gpu.gpu_compare.LowLatencyStylusActivityGpuCompare;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        Button buttonCpu = findViewById(R.id.button_cpu);
        Button buttonCpuCompare = findViewById(R.id.button_cpu_compare);
        Button buttonGpu = findViewById(R.id.button_gpu);
        Button buttonGpuCompare = findViewById(R.id.button_gpu_compare);

        // Set up buttons to launch the appropriate activity
        buttonCpu.setOnClickListener((View v) -> startActivity(
                new Intent(MainActivity.this, LowLatencyStylusActivityCpu.class)));
        buttonCpuCompare.setOnClickListener((View v) -> startActivity(
                new Intent(MainActivity.this, LowLatencyStylusActivityCpuCompare.class)));
        buttonGpu.setOnClickListener((View v) -> startActivity(
                new Intent(MainActivity.this, LowLatencyStylusActivityGpu.class)));
        buttonGpuCompare.setOnClickListener((View v) -> startActivity(
                new Intent(MainActivity.this, LowLatencyStylusActivityGpuCompare.class)));
    }
}