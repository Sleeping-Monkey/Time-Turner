/*
 * Copyright (C) The Android Open Source Project
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
package com.facetracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Switch;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.LargestFaceFocusingProcessor;

import java.io.IOException;

/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */
public final class FaceTrackerActivity extends AppCompatActivity {
    private static final String TAG = "FaceTracker";

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main);

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        detector.setProcessor(new LargestFaceFocusingProcessor(detector, new EyesTracker(context, mGraphicOverlay)));

//        detector.setProcessor(
//                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
//                        .build());

        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        mCameraSource = new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(640, 480)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();

        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Tracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    private class EyesTracker extends Tracker<Face> {

        private final float THRESHOLD = 0.50f;
        private MediaPlayer mp        = null;
        private Context context;
        private int TIME_THRESHOLD = 0;
        private int TIME_THRESHOLD_MAX = 28;
        private ImageButton eyeicon = null;
        private Switch on_off = null;
        private boolean on = false;

        private NotificationCompat.Builder builder;
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;
        private NotificationManager NM;

        @RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        private EyesTracker(Context context, GraphicOverlay overlay) {
            this.context = context;
            eyeicon = (ImageButton) findViewById(R.id.eyeicon);
            on_off = (Switch)findViewById(R.id.on_off);
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
            on_off.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    on = isChecked;
                }
            });

            builder = new NotificationCompat.Builder(context);
            builder.setVibrate(new long[] {1000, 1000, 0, 0 ,0, 1000, 1000, 0, 1000});
            builder.setSmallIcon(R.drawable.closed);
            builder.setPriority(2);
            builder.setContentTitle("Sos!");
            builder.setContentText("...");
            NM = (NotificationManager) getApplicationContext().getSystemService(NOTIFICATION_SERVICE);
        }


        @SuppressLint("NewApi")
        @Override
        public void onUpdate(Detector.Detections<Face> detections, Face face) {
            if (!on) {
                if (isSoundPlaying()) {
                    stopSound();
                }
                eyeicon.setBackgroundResource(R.drawable.open);
                return;
            }
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
            if (TIME_THRESHOLD == TIME_THRESHOLD_MAX && !isSoundPlaying())
                playSound(R.raw.ugly);
            if (face.getIsLeftEyeOpenProbability() > THRESHOLD && face.getIsRightEyeOpenProbability() > THRESHOLD) {
                Log.d(TAG, "Eyes open");
                eyeicon.setBackgroundResource(R.drawable.open);
                if (TIME_THRESHOLD > 0) {
                    TIME_THRESHOLD -=3;
                }
                else if (isSoundPlaying()) {
                    stopSound();
                }
            }
            else {
                if (TIME_THRESHOLD > 20) {
                    Notifi();
                    eyeicon.setBackgroundResource(R.drawable.closed);
                }
                Log.d(TAG, "Eyes closed: TIME_THRESHOLD: " + TIME_THRESHOLD);
                if (TIME_THRESHOLD < TIME_THRESHOLD_MAX)
                    TIME_THRESHOLD++;
            }
        }


        @Override
        public void onMissing(Detector.Detections<Face> detections) {
            super.onMissing(detections);
            if (!on) {
                if (isSoundPlaying()) {
                    stopSound();
                }
                eyeicon.setBackgroundResource(R.drawable.open);
                return;
            }
            if (TIME_THRESHOLD > 5)
                eyeicon.setBackgroundResource(R.drawable.closed);
            Log.d(TAG, "Eyes missing");
            if (!isSoundPlaying())
            {
                Notifi();
                playSound(R.raw.ugly);
            }
            mOverlay.remove(mFaceGraphic);
        }

        synchronized private void Notifi()
        {
            Notification notification = builder.build();
            NM.notify("do not sleep!", 0, notification);
        }

        private void stopSound()
        {
            if (mp != null && isSoundPlaying())
            {
                mp.reset();
                mp.release();
                mp = null;
            }
        }

        private boolean isSoundPlaying()
        {
            return (mp != null && mp.isPlaying());
        }

        private void playSound(int sound)
        {
            stopSound();
            mp = MediaPlayer.create(context, sound);
            mp.start();
        }

        @Override
        public void onDone() {
            super.onDone();
            mOverlay.remove(mFaceGraphic);
        }
    }

}
