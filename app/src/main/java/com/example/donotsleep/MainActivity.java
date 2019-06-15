package com.example.donotsleep;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.LargestFaceFocusingProcessor;

import java.io.IOException;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private MyCameraView preview;
    private CameraSource cameraSource;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preview = findViewById(R.id.preview);
        requestCameraPermission();
        createCameraSource();
        //preview.start(cameraSource);
    }

    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
        }
    }
    @SuppressLint("MissingPermission")
    private void createCameraSource() {
        Context activityContext = this.getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(activityContext).setTrackingEnabled(true).setClassificationType(FaceDetector.ALL_CLASSIFICATIONS).setMode(FaceDetector.FAST_MODE).build();

        detector.setProcessor(new LargestFaceFocusingProcessor(detector, new EyesTracker()));

        cameraSource = new CameraSource.Builder(
                activityContext, detector
        ).setRequestedPreviewSize(
            1024, 768).setFacing(
                    CameraSource.CAMERA_FACING_FRONT
        ).setRequestedFps(30.0f).build();

        try {
            cameraSource.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private class EyesTracker extends Tracker<Face> {

        private final float THRESHOLD = 0.75f;

        private EyesTracker() {

        }

        @Override
        public void onUpdate(Detector.Detections<Face> detections, Face face) {
            if (face.getIsLeftEyeOpenProbability() > THRESHOLD || face.getIsRightEyeOpenProbability() > THRESHOLD) {
                Log.i(TAG, "Eyes open");
            }
            else {
                Log.i(TAG, "Eyes closed");
            }
        }

        @Override
        public void onMissing(Detector.Detections<Face> detections) {
            super.onMissing(detections);
            Log.i(TAG, "Eyes closed");
        }

        @Override
        public void onDone() {
            super.onDone();
        }
    }
}