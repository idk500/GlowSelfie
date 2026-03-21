package com.glowselfie.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements SurfaceHolder.Callback {

    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";

    private Camera camera;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private boolean isCameraStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.previewView);
        Button captureButton = findViewById(R.id.captureButton);
        SeekBar intensitySlider = findViewById(R.id.intensitySlider);
        SeekBar hueSlider = findViewById(R.id.hueSlider);

        getWindow().getDecorView().setBackgroundColor(0xFFFF6B9D);

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
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

    private boolean checkCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "相机权限被拒绝", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCamera() {
        try {
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            Camera.Parameters parameters = camera.getParameters();
            camera.setParameters(parameters);
            isCameraStarted = true;
        } catch (Exception e) {
            Toast.makeText(this, "无法打开相机: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void takePhoto() {
        if (camera == null) {
            Toast.makeText(this, "相机未就绪", Toast.LENGTH_SHORT).show();
            return;
        }

        camera.takePicture(null, null, (data, camera) -> {
            File photoFile = new File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(new Date()) + ".jpg"
            );

            try (FileOutputStream fos = new FileOutputStream(photoFile)) {
                fos.write(data);
                Toast.makeText(MainActivity.this, 
                    "照片已保存: " + photoFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Toast.makeText(MainActivity.this, 
                    "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            camera.startPreview();
        });
    }

    private void updateBackgroundColor(int intensity, int hue) {
        float normalizedIntensity = intensity / 100f;
        float hueInDegrees = hue * 3.6f;
        float[] hsv = {hueInDegrees, 0.7f * normalizedIntensity, 1.0f};
        int color = Color.HSVToColor(255, hsv);
        getWindow().getDecorView().setBackgroundColor(color);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (camera != null) {
            try {
                camera.setPreviewDisplay(holder);
                camera.setDisplayOrientation(90);
                camera.startPreview();
            } catch (IOException e) {
                Toast.makeText(this, "预览失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (holder.getSurface() == null) return;
        try {
            camera.stopPreview();
            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (Exception e) {
            // Ignore
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (camera != null) {
            camera.stopPreview();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isCameraStarted && camera == null) {
            startCamera();
        }
        if (camera != null && surfaceHolder.getSurface() != null) {
            try {
                camera.setPreviewDisplay(surfaceHolder);
                camera.startPreview();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera != null) {
            camera.stopPreview();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }
}
