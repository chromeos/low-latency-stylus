# Low-Latency Stylus for Chrome OS

Alpha 1.0.1

Updated 12 May 2021

A low-latency stylus library for Android apps on Chrome OS. This library
provides mechanisms to reduce the touch-to-draw latency on Chrome
OS devices by using direct rendering and prediction.

A simple demo app is provided showing both the CPU and GPU-driven
low-latency implementations as well as a side-by-side comparison for
each implementation with a regular (not low-latency) canvas.

Please file bugs and features requests to help us improve the library as
we move toward launch.


## How it works

The low-latency stylus library achieves fast stylus-to-screen response
by leveraging two mechanisms.


1. **Direct-rendering** **-** by rendering pen strokes directly through
   the hardware compositor, delays due to OS compositing can be avoided
2. **Prediction** **-** there will always be some latency in drawing
   response to screen response time, hardware limitations, and required
   OS functionality. The library attempts to compensate for this
   remaining latency using prediction - guessing where the next part of
   the stroke will be drawn based on the current speed and direction of
   the stroke - and can achieve what feels like “zero-latency”.


## Getting the library


1. In your top-level **build.gradle** file, be sure you have google() as
   one of your maven repositories:

   ```
   repositories { 
       // low-latency libraries are stored in google's maven repository
       google() 
       mavenCentral() 
   }
   ```
2. Put the following in the dependencies section of the app-level
   **build.gradle** file:

   ```
   implementation 'com.google.chromeos:chromeos-lowlatencystylus:1.0.1
   ```


## Using the library

First, decide if you would like to use the CPU-based version of the
library or the GPU-based version.


* **CPU:** The CPU version is easier to implement but may not be
  suitable for apps with complex brushes and graphics requirements.
* **GPU:** The GPU version requires some OpenGL knowledge to use and
  offers some performance improvements over the CPU version and allows
  for more advanced brushes.

Demo applications and instructions are provided for both versions of the
library.


>**NOTE:** your Android runtime version on Chrome OS must be greater
>than 7316937. To check this, go to chrome://version in the browser and
>read the ARC line. You may need to change your device's update channel
>to the beta channel or dev channel to get this version.


## CPU Library

The best way to get started using the CPU library is to examine the
`LowLatencyStylusDemo` app. Import it into Android Studio and use the
`cpu` package and the `LowLatencyStylusActivityCpu` activity as your
reference. You may also want to reference the API documentation provided
here.


### Create your main canvas

Create a drawing canvas for your main drawing surface that will receive
`MotionEvents` and show the completed drawing gestures. In the demo app,
a reference class is provided called `SampleInkView`.


### Create an InkOverlayView

Create an `InkOverlayView` that will sit on top of your main canvas.
This is the view that will show the current, incomplete, drawing gesture
as well as the predicted path of the current gesture. Correctly tuned
prediction can give the appearance of zero-latency drawing.


### Extend InkDelegate

Drawing gestures passed to an `InkOverlayView` will be processed via an
`InkDelegate`. Extending the `InkDelegate` class allows you to control
how the current drawing gesture and predicted gestures will be drawn. An
example of how this is shown in the `SampleInkDelegate` class of the
demo app.


### Pass current gestures to the InkOverlayView

Your main canvas should receive `MotionEvents`, keep track of them, and
show any completed drawing gestures. The `MotionEvents` for the current,
incomplete, drawing gesture should be passed to the `InkOverlayView`
using `InkOverlayView.onTouch`.


### Commit to main canvas

When a stroke is finished or cancelled (like due to
[palm rejection](https://developer.android.com/topic/arc/input-compatibility#palm_rejection)),
and lines representing the real completed strokes have been drawn to the
main canvas, the `InkOverlayView` should be cleared. In the demo app,
this is done in the `onTouchEvent` method of the `SampleInkView` class.


### Using/disabling prediction

Set latency compensation level for the prediction algorithm in ms using
`setPredictionTarget `on your `InkOverlayView.` You can disable
prediction by setting this to **0**. Use the `Prediction` slider in the
demo app to tune the prediction value you want. Note: setting this value
too high will result in noticeable over-prediction.


### Testing the library

You should notice a decrease in latency on Chrome OS devices when using
the library. Test it out for a given device using the demo application.
You can test the difference with and without low-latency by using the
side-by-side comparison Activity provided in the demo application.

Note: pressing `[SPACE]` will clear the canvas in the demo application.


## GPU Library

The best way to get started using the GPU library is to examine the
`LowLatencyStylusDemo` app. Import it into Android Studio and use the
`gpu` package and the `LowLatencyStylusActivityGpu` activity as your
reference. You may also want to reference the API documentation provided
here.


### Create your main canvas

The main low-latency canvas will be subclassed from `GLInkSurfaceView`.
To set up this view:


1. Set the prediction target in milliseconds (or disable prediction by
   setting target to 0)
2. Set the `GLInkRenderer` to handle the rendering
3. Receive input events and queue them correctly to the `GLInkRenderer`

Input events are generally handled by looking for
[ACTION\_DOWN](https://developer.android.com/reference/android/view/MotionEvent#ACTION_DOWN),
[ACTION\_MOVE](https://developer.android.com/reference/android/view/MotionEvent#ACTION_MOVE),
and
[ACTION\_UP](https://developer.android.com/reference/android/view/MotionEvent#ACTION_UP)
events in the
[onTouchEvent](https://developer.android.com/reference/android/view/View#onTouchEvent(android.view.MotionEvent))
callback. Note that
[ACTION\_MOVE](https://developer.android.com/reference/android/view/MotionEvent#ACTION_MOVE)
can contain a *list* of intermediate points that have been batched by
the OS’s input logic. See
[Batching](https://developer.android.com/reference/android/view/MotionEvent#batching)
in the Android documentation for more details.

To draw on the low-latency surface,
[MotionEvents](https://developer.android.com/reference/android/view/MotionEvent)
must be passed down to the `GLInkSurfaceView.onTouch` method. This
method will automatically request unbuffered dispatch of input events
for you to ensure that data is received as fast as possible.

>**Note:** If you are not directly passing down MotionEvents received
>from the system - for example if you are interpreting the events and
>constructing new ones before sending them to `GLInkSurfaceView.onTouch`
>\- you should call
>[View.requestUnbufferedDispatch](https://developer.android.com/reference/android/view/View#requestUnbufferedDispatch(android.view.MotionEvent))
>manually every time you receive a system
>[ACTION\_DOWN](https://developer.android.com/reference/android/view/MotionEvent#ACTION_DOWN)
>event, in order to get the lowest input latency.


### Set your GLInkRenderer

Your `GLInkRenderer` should handle your brushes/shaders as well as
manage incremental damage regions to provide the best drawing
performance. Reference the included `SampleInkRenderer` as a starting
point.

To configure the renderer:

1. Manage OpenGL shaders for drawing brush strokes
2. Keep track of damaged regions
3. Handle surface related callbacks (clear, onSurfaceCreated,
   onSurfaceChanged)
4. Override `beforeDraw` to handle input events received from the
   `GLInkSurfaceView` to be correctly passed to shaders and to return
   the damage area
5. Override `onDraw` to execute the draw

>**Note:** the damage Rect returned from `beforeDraw` will be passed to
>an internal
>[glScissor](https://www.khronos.org/registry/OpenGL-Refpages/es2.0/xhtml/glScissor.xml)
>call before the render call is made.


### Brushes/Shaders

You will need to create OpenGL shaders for your different brushes to
handle the actual rendering of brush strokes received from the
`GLInkRenderer`. The provided `BrushShader` class gives an example of a
simple line brush.


### Using/disabling prediction

As with the CPU library, the `setPredictionTargetMs` method in
`GLInkSurfaceView` allows you to set your desired level of
prediction. You can disable prediction by setting this
to **0**.


### View and projection matrices

In the demo app, the view and projection matrices are set to the
identity matrix. The API allows you to change this. If you are using
different matrices in your application, please let us know. We would
like feedback on this part of the API.


### Testing the library

You should notice a decrease in latency on Chrome OS devices when using
the library. Test it out for a given device using the demo application.
You can test the difference with and without low-latency by using the
side-by-side comparison Activity provided in the demo application.

Note: pressing `[SPACE]` will clear the canvas in the demo application.


## Known Issues

* GPU prediction is currently not working in the demo.

* Display scaling: in order to leverage direct compositing, the user
  needs to have their Chrome OS display resolution set to the “default”
  value. With other settings, additional GPU calculations may be needed
  to scale the output which makes direct rendering not possible.


## Feedback and Bugs

We appreciate you exercising this library and strongly value your
feedback. Please file bugs and feature requests here on the issue
tracker.

<img alt="Screenshot of LowLatencyStylusDemo on a Chromebook" src="https://github.com/chromeos/low-latency-stylus/blob/main/images/screenshot.png" />


## This is not an officially supported Google product

## LICENSE
***

Copyright 2021 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
