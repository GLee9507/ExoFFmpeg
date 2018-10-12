/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.ui.spherical;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.BinderThread;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.util.AttributeSet;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.spherical.ProjectionRenderer.EyeType;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Renders a GL scene in a non-VR Activity that is affected by phone orientation and touch input.
 *
 * <p>The two input components are the TYPE_GAME_ROTATION_VECTOR Sensor and a TouchListener. The GL
 * renderer combines these two inputs to render a scene with the appropriate camera orientation.
 *
 * <p>The primary complexity in this class is related to the various rotations. It is important to
 * apply the touch and sensor rotations in the correct order or the user's touch manipulations won't
 * match what they expect.
 */
@TargetApi(15)
public final class SphericalSurfaceView extends GLSurfaceView {

  /**
   * This listener can be used to be notified when the {@link Surface} associated with this view is
   * changed.
   */
  public interface SurfaceListener {
    /**
     * Invoked when the surface is changed or there isn't one anymore. Any previous surface
     * shouldn't be used after this call.
     *
     * @param surface The new surface or null if there isn't one anymore.
     */
    void surfaceChanged(@Nullable Surface surface);
  }

  // Arbitrary vertical field of view.
  private static final int FIELD_OF_VIEW_DEGREES = 90;
  private static final float Z_NEAR = .1f;
  private static final float Z_FAR = 100;

  // TODO Calculate this depending on surface size and field of view.
  private static final float PX_PER_DEGREES = 25;

  /*package*/ static final float UPRIGHT_ROLL = (float) Math.PI;

  private final SensorManager sensorManager;
  private final @Nullable Sensor orientationSensor;
  private final PhoneOrientationListener phoneOrientationListener;
  private final Renderer renderer;
  private final Handler mainHandler;
  private final TouchTracker touchTracker;
  private final SceneRenderer scene;
  private @Nullable SurfaceListener surfaceListener;
  private @Nullable SurfaceTexture surfaceTexture;
  private @Nullable Surface surface;
  private @Nullable Player.VideoComponent videoComponent;

  public SphericalSurfaceView(Context context) {
    this(context, null);
  }

  public SphericalSurfaceView(Context context, @Nullable AttributeSet attributeSet) {
    super(context, attributeSet);
    mainHandler = new Handler(Looper.getMainLooper());

    // Configure sensors and touch.
    sensorManager =
        (SensorManager) Assertions.checkNotNull(context.getSystemService(Context.SENSOR_SERVICE));
    // TYPE_GAME_ROTATION_VECTOR is the easiest sensor since it handles all the complex math for
    // fusion. It's used instead of TYPE_ROTATION_VECTOR since the latter uses the magnetometer on
    // devices. When used indoors, the magnetometer can take some time to settle depending on the
    // device and amount of metal in the environment.
    int type = Util.SDK_INT >= 18 ? Sensor.TYPE_GAME_ROTATION_VECTOR : Sensor.TYPE_ROTATION_VECTOR;
    orientationSensor = sensorManager.getDefaultSensor(type);

    scene = new SceneRenderer();
    renderer = new Renderer(scene);

    touchTracker = new TouchTracker(context, renderer, PX_PER_DEGREES);
    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    Display display = Assertions.checkNotNull(windowManager).getDefaultDisplay();
    phoneOrientationListener = new PhoneOrientationListener(display, touchTracker, renderer);

    setEGLContextClientVersion(2);
    setRenderer(renderer);
    setOnTouchListener(touchTracker);
  }

  /**
   * Sets the default stereo mode. If the played video doesn't contain a stereo mode the default one
   * is used.
   *
   * @param stereoMode A {@link C.StereoMode} value.
   */
  public void setDefaultStereoMode(@C.StereoMode int stereoMode) {
    scene.setDefaultStereoMode(stereoMode);
  }

  /** Sets the {@link Player.VideoComponent} to use. */
  public void setVideoComponent(@Nullable Player.VideoComponent newVideoComponent) {
    if (newVideoComponent == videoComponent) {
      return;
    }
    if (videoComponent != null) {
      if (surface != null) {
        videoComponent.clearVideoSurface(surface);
      }
      videoComponent.clearVideoFrameMetadataListener(scene);
      videoComponent.clearCameraMotionListener(scene);
    }
    videoComponent = newVideoComponent;
    if (videoComponent != null) {
      videoComponent.setVideoFrameMetadataListener(scene);
      videoComponent.setCameraMotionListener(scene);
      videoComponent.setVideoSurface(surface);
    }
  }

  /**
   * Sets the {@link SurfaceListener} used to listen to surface events.
   *
   * @param listener The listener for surface events.
   */
  public void setSurfaceListener(@Nullable SurfaceListener listener) {
    surfaceListener = listener;
  }

  /** Sets the {@link SingleTapListener} used to listen to single tap events on this view. */
  public void setSingleTapListener(@Nullable SingleTapListener listener) {
    touchTracker.setSingleTapListener(listener);
  }

  @Override
  public void onResume() {
    super.onResume();
    if (orientationSensor != null) {
      sensorManager.registerListener(
          phoneOrientationListener, orientationSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }
  }

  @Override
  public void onPause() {
    if (orientationSensor != null) {
      sensorManager.unregisterListener(phoneOrientationListener);
    }
    super.onPause();
  }

  @Override
  protected void onDetachedFromWindow() {
    // This call stops GL thread.
    super.onDetachedFromWindow();

    // Post to make sure we occur in order with any onSurfaceTextureAvailable calls.
    mainHandler.post(
        () -> {
          if (surface != null) {
            if (surfaceListener != null) {
              surfaceListener.surfaceChanged(null);
            }
            releaseSurface(surfaceTexture, surface);
            surfaceTexture = null;
            surface = null;
          }
        });
  }

  // Called on GL thread.
  private void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture) {
    mainHandler.post(
        () -> {
          SurfaceTexture oldSurfaceTexture = this.surfaceTexture;
          Surface oldSurface = this.surface;
          this.surfaceTexture = surfaceTexture;
          this.surface = new Surface(surfaceTexture);
          if (surfaceListener != null) {
            surfaceListener.surfaceChanged(surface);
          }
          releaseSurface(oldSurfaceTexture, oldSurface);
        });
  }

  private static void releaseSurface(
      @Nullable SurfaceTexture oldSurfaceTexture, @Nullable Surface oldSurface) {
    if (oldSurfaceTexture != null) {
      oldSurfaceTexture.release();
    }
    if (oldSurface != null) {
      oldSurface.release();
    }
  }

  /** Detects sensor events and saves them as a matrix. */
  private static class PhoneOrientationListener implements SensorEventListener {
    private final float[] phoneInWorldSpaceMatrix = new float[16];
    private final float[] remappedPhoneMatrix = new float[16];
    private final float[] angles = new float[3];
    private final Display display;
    private final TouchTracker touchTracker;
    private final Renderer renderer;

    public PhoneOrientationListener(Display display, TouchTracker touchTracker, Renderer renderer) {
      this.display = display;
      this.touchTracker = touchTracker;
      this.renderer = renderer;
    }

    @Override
    @BinderThread
    public void onSensorChanged(SensorEvent event) {
      SensorManager.getRotationMatrixFromVector(remappedPhoneMatrix, event.values);

      // If we're not in upright portrait mode, remap the axes of the coordinate system according to
      // the display rotation.
      int xAxis;
      int yAxis;
      switch (display.getRotation()) {
        case Surface.ROTATION_270:
          xAxis = SensorManager.AXIS_MINUS_Y;
          yAxis = SensorManager.AXIS_X;
          break;
        case Surface.ROTATION_180:
          xAxis = SensorManager.AXIS_MINUS_X;
          yAxis = SensorManager.AXIS_MINUS_Y;
          break;
        case Surface.ROTATION_90:
          xAxis = SensorManager.AXIS_Y;
          yAxis = SensorManager.AXIS_MINUS_X;
          break;
        case Surface.ROTATION_0:
        default:
          xAxis = SensorManager.AXIS_X;
          yAxis = SensorManager.AXIS_Y;
          break;
      }
      SensorManager.remapCoordinateSystem(
          remappedPhoneMatrix, xAxis, yAxis, phoneInWorldSpaceMatrix);

      // Extract the phone's roll and pass it on to touchTracker & renderer. Remapping is required
      // since we need the calculated roll of the phone to be independent of the phone's pitch &
      // yaw. Any operation that decomposes rotation to Euler angles needs to be performed
      // carefully.
      SensorManager.remapCoordinateSystem(
          phoneInWorldSpaceMatrix,
          SensorManager.AXIS_X,
          SensorManager.AXIS_MINUS_Z,
          remappedPhoneMatrix);
      SensorManager.getOrientation(remappedPhoneMatrix, angles);
      float roll = angles[2];
      touchTracker.setRoll(roll);

      // Rotate from Android coordinates to OpenGL coordinates. Android's coordinate system
      // assumes Y points North and Z points to the sky. OpenGL has Y pointing up and Z pointing
      // toward the user.
      Matrix.rotateM(phoneInWorldSpaceMatrix, 0, 90, 1, 0, 0);
      renderer.setDeviceOrientation(phoneInWorldSpaceMatrix, roll);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
  }

  /**
   * Standard GL Renderer implementation. The notable code is the matrix multiplication in
   * onDrawFrame and updatePitchMatrix.
   */
  // @VisibleForTesting
  /*package*/ class Renderer implements GLSurfaceView.Renderer, TouchTracker.Listener {
    private final SceneRenderer scene;
    private final float[] projectionMatrix = new float[16];

    // There is no model matrix for this scene so viewProjectionMatrix is used for the mvpMatrix.
    private final float[] viewProjectionMatrix = new float[16];

    // Device orientation is derived from sensor data. This is accessed in the sensor's thread and
    // the GL thread.
    private final float[] deviceOrientationMatrix = new float[16];

    // Optional pitch and yaw rotations are applied to the sensor orientation. These are accessed on
    // the UI, sensor and GL Threads.
    private final float[] touchPitchMatrix = new float[16];
    private final float[] touchYawMatrix = new float[16];
    private float touchPitch;
    private float deviceRoll;

    // viewMatrix = touchPitch * deviceOrientation * touchYaw.
    private final float[] viewMatrix = new float[16];
    private final float[] tempMatrix = new float[16];

    public Renderer(SceneRenderer scene) {
      this.scene = scene;
      Matrix.setIdentityM(deviceOrientationMatrix, 0);
      Matrix.setIdentityM(touchPitchMatrix, 0);
      Matrix.setIdentityM(touchYawMatrix, 0);
      deviceRoll = UPRIGHT_ROLL;
    }

    @Override
    public synchronized void onSurfaceCreated(GL10 gl, EGLConfig config) {
      onSurfaceTextureAvailable(scene.init());
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
      GLES20.glViewport(0, 0, width, height);
      float aspect = (float) width / height;
      float fovY = calculateFieldOfViewInYDirection(aspect);
      Matrix.perspectiveM(projectionMatrix, 0, fovY, aspect, Z_NEAR, Z_FAR);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
      // Combine touch & sensor data.
      // Orientation = pitch * sensor * yaw since that is closest to what most users expect the
      // behavior to be.
      synchronized (this) {
        Matrix.multiplyMM(tempMatrix, 0, deviceOrientationMatrix, 0, touchYawMatrix, 0);
        Matrix.multiplyMM(viewMatrix, 0, touchPitchMatrix, 0, tempMatrix, 0);
      }

      Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
      scene.drawFrame(viewProjectionMatrix, EyeType.MONOCULAR);
    }

    /** Adjusts the GL camera's rotation based on device rotation. Runs on the sensor thread. */
    @BinderThread
    public synchronized void setDeviceOrientation(float[] matrix, float deviceRoll) {
      System.arraycopy(matrix, 0, deviceOrientationMatrix, 0, deviceOrientationMatrix.length);
      this.deviceRoll = -deviceRoll;
      updatePitchMatrix();
    }

    /**
     * Updates the pitch matrix after a physical rotation or touch input. The pitch matrix rotation
     * is applied on an axis that is dependent on device rotation so this must be called after
     * either touch or sensor update.
     */
    @AnyThread
    private void updatePitchMatrix() {
      // The camera's pitch needs to be rotated along an axis that is parallel to the real world's
      // horizon. This is the <1, 0, 0> axis after compensating for the device's roll.
      Matrix.setRotateM(
          touchPitchMatrix,
          0,
          -touchPitch,
          (float) Math.cos(deviceRoll),
          (float) Math.sin(deviceRoll),
          0);
    }

    @Override
    @UiThread
    public synchronized void onScrollChange(PointF scrollOffsetDegrees) {
      touchPitch = scrollOffsetDegrees.y;
      updatePitchMatrix();
      Matrix.setRotateM(touchYawMatrix, 0, -scrollOffsetDegrees.x, 0, 1, 0);
    }

    private float calculateFieldOfViewInYDirection(float aspect) {
      boolean landscapeMode = aspect > 1;
      if (landscapeMode) {
        double halfFovX = FIELD_OF_VIEW_DEGREES / 2;
        double tanY = Math.tan(Math.toRadians(halfFovX)) / aspect;
        double halfFovY = Math.toDegrees(Math.atan(tanY));
        return (float) (halfFovY * 2);
      } else {
        return FIELD_OF_VIEW_DEGREES;
      }
    }
  }
}
