package com.glowselfie.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ExecutorService cameraExecutor;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private CameraControl cameraControl;
    private CameraInfo cameraInfo;
    private ActivityResultLauncher<String[]> activityResultLauncher;

    private final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        Button captureButton = findViewById(R.id.captureButton);
        SeekBar intensitySlider = findViewById(R.id.intensitySlider);
        SeekBar hueSlider = findViewById(R.id.hueSlider);

        getWindow().getDecorView().setBackgroundColor(0xFFFF6B9D);

        cameraExecutor = Executors.newSingleThreadExecutor();

        activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> {
                boolean permissionGranted = true;
                for (String permission : REQUIRED_PERMISSIONS) {
                    if (!permissions.getOrDefault(permission, false)) {
                        permissionGranted = false;
                    }
                }
                if (!permissionGranted) {
                    Toast.makeText(this, "权限被拒绝", Toast.LENGTH_SHORT).show();
                } else {
                    startCamera();
                }
            }
        );

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            activityResultLauncher.launch(REQUIRED_PERMISSIONS);
        }

        captureButton.setOnClickListener(v -> takePhoto());

        intensitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateBackgroundColor(progress, hueSlider.getProgress());
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        hueSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateBackgroundColor(intensitySlider.getProgress(), progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void startCamera() {
        ProcessCameraProvider cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                cameraProvider.unbindAll();

                Camera camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                );

                cameraControl = camera.getCameraControl();
                cameraInfo = camera.getCameraInfo();

            } catch (Exception exc) {
                Toast.makeText(this, "相机启动失败: " + exc.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        File photoFile = new File(
            getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),
            new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(new Date()) + ".jpg"
        );

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onError(@NonNull ImageCaptureException exc) {
                    Toast.makeText(MainActivity.this, "拍照失败: " + exc.getMessage(), Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                    Toast.makeText(MainActivity.this, "照片已保存: " + photoFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                }
            }
        );
    }

    private void updateBackgroundColor(int intensity, int hue) {
        float normalizedIntensity = intensity / 100f;
        float hueInDegrees = hue * 3.6f;

        float[] hsv = {hueInDegrees, 0.7f * normalizedIntensity, 1.0f};
        int color = android.graphics.Color.HSVToColor(255, hsv);

        getWindow().getDecorView().setBackgroundColor(color);
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
