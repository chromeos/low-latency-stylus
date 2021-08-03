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

import android.graphics.PointF;

public class DrawPoint {
    public PointF point = new PointF(0f, 0f);
    public float red = 0f;
    public float green = 0f;
    public float blue = 0f;

    public DrawPoint(PointF point) {
        this.point = point;
    }
    public DrawPoint(float x, float y) {
        this.point.x = x;
        this.point.y = y;
    }
    public DrawPoint(PointF point, float red, float green, float blue) {
        this.point = point;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }
}
