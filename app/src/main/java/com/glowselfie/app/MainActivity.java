package com.glowselfie.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

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
    private FrameLayout previewContainer;
    private SurfaceHolder surfaceHolder;
    private SeekBar intensitySlider;
    private SeekBar hueSlider;
    private TextView intensityValueText;
    private TextView hueValueText;
    private TextView helpText;
    private Button openGalleryButton;
    private Button resizePreviewButton;
    private Switch neonModeSwitch;
    private View charmModeBackground;
    private View charmLeftPanel;
    private View charmRightPanel;
    private boolean isCameraStarted = false;
    private File lastPhotoFile;
    private boolean isNeonMode = false;

    private float currentPreviewAspect = 4f / 3f;
    private final int[] previewWidthDpSteps = {140, 180, 220};
    private int previewSizeStepIndex = 1;

    private float touchDownRawX;
    private float touchDownRawY;
    private float viewDownX;
    private float viewDownY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setWindowBrightnessMax();

        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.previewView);
        previewContainer = findViewById(R.id.previewContainer);
        Button captureButton = findViewById(R.id.captureButton);
        openGalleryButton = findViewById(R.id.openGalleryButton);
        intensitySlider = findViewById(R.id.intensitySlider);
        hueSlider = findViewById(R.id.hueSlider);
        intensityValueText = findViewById(R.id.intensityValueText);
        hueValueText = findViewById(R.id.hueValueText);
        helpText = findViewById(R.id.helpText);
        resizePreviewButton = findViewById(R.id.resizePreviewButton);
        neonModeSwitch = findViewById(R.id.neonModeSwitch);
        charmModeBackground = findViewById(R.id.charmModeBackground);
        charmLeftPanel = findViewById(R.id.charmLeftPanel);
        charmRightPanel = findViewById(R.id.charmRightPanel);

        updateBackgroundColor(intensitySlider.getProgress(), hueSlider.getProgress());
        setupPreviewInteractions();

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }

        captureButton.setOnClickListener(v -> takePhoto());
        openGalleryButton.setOnClickListener(v -> openLastPhoto());
        resizePreviewButton.setOnClickListener(v -> {
            previewSizeStepIndex = (previewSizeStepIndex + 1) % previewWidthDpSteps.length;
            applyPreviewContainerSize(currentPreviewAspect);
            Toast.makeText(this, "预览框大小已切换", Toast.LENGTH_SHORT).show();
        });
        neonModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isNeonMode = isChecked;
            hueSlider.setEnabled(!isChecked);
            applyCharmModeLayout(isChecked);
            updateBackgroundColor(intensitySlider.getProgress(), hueSlider.getProgress());
        });

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

    private void setWindowBrightnessMax() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = 1.0f;
        getWindow().setAttributes(params);
    }

    private void setupPreviewInteractions() {
        previewContainer.setOnTouchListener((v, event) -> {
            View parent = (View) v.getParent();
            if (parent == null) return false;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchDownRawX = event.getRawX();
                    touchDownRawY = event.getRawY();
                    viewDownX = v.getX();
                    viewDownY = v.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - touchDownRawX;
                    float dy = event.getRawY() - touchDownRawY;
                    float newX = clamp(viewDownX + dx, 0f, Math.max(0f, parent.getWidth() - v.getWidth()));
                    float newY = clamp(viewDownY + dy, 0f, Math.max(0f, parent.getHeight() - v.getHeight()));
                    v.setX(newX);
                    v.setY(newY);
                    return true;
                default:
                    return false;
            }
        });
    }

    private float clamp(float value, float min, float max) {
        if (value < min) return min;
        return Math.min(value, max);
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
            if (camera == null) {
                camera = Camera.open();
            }
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size previewSize = choosePreviewSize(parameters);
            if (previewSize != null) {
                parameters.setPreviewSize(previewSize.width, previewSize.height);
                currentPreviewAspect = previewSize.width / (float) previewSize.height;
            }
            camera.setParameters(parameters);
            applyPreviewContainerSize(currentPreviewAspect);
            isCameraStarted = true;

            if (surfaceHolder != null && surfaceHolder.getSurface() != null) {
                startPreviewSafely();
            }
        } catch (Exception e) {
            Toast.makeText(this, "无法打开相机: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Camera.Size choosePreviewSize(Camera.Parameters parameters) {
        Camera.Size best = null;
        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (best == null) {
                best = size;
                continue;
            }
            if (size.width * size.height > best.width * best.height) {
                best = size;
            }
        }
        return best;
    }

    private void applyPreviewContainerSize(float aspectPortrait) {
        int widthDp = previewWidthDpSteps[previewSizeStepIndex];
        int widthPx = dpToPx(widthDp);
        int heightPx = (int) (widthPx * aspectPortrait);
        int minHeight = dpToPx(140);
        int maxHeight = dpToPx(300);
        if (heightPx < minHeight) heightPx = minHeight;
        if (heightPx > maxHeight) heightPx = maxHeight;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) previewContainer.getLayoutParams();
        lp.width = widthPx;
        lp.height = heightPx;
        previewContainer.setLayoutParams(lp);

        if (isNeonMode) {
            centerPreviewContainer();
        } else {
            positionPreviewTopCenter();
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
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
                lastPhotoFile = photoFile;
                openGalleryButton.setEnabled(true);
                Toast.makeText(MainActivity.this, 
                    "照片已保存: " + photoFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Toast.makeText(MainActivity.this, 
                    "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            camera.startPreview();
        });
    }

    private void openLastPhoto() {
        if (lastPhotoFile == null || !lastPhotoFile.exists()) {
            Toast.makeText(this, "还没有可查看的照片", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", lastPhotoFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "未找到可打开图片的应用", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "打开照片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateBackgroundColor(int intensity, int hue) {
        float normalizedIntensity = intensity / 100f;
        float hueInDegrees = mapHueToPinkRange(hue);

        if (isNeonMode) {
            applyCharmSplitBackground(normalizedIntensity);
        } else {
            charmModeBackground.setVisibility(View.GONE);
            float saturation = 0.35f + 0.55f * normalizedIntensity;
            float value = 0.45f + 0.55f * normalizedIntensity;
            float[] hsv = {hueInDegrees, saturation, value};
            int color = Color.HSVToColor(255, hsv);
            getWindow().getDecorView().setBackgroundColor(color);
        }

        intensityValueText.setText(getString(R.string.intensity_value, intensity));
        hueValueText.setText(getString(R.string.hue_value, Math.round(hueInDegrees)));

        if (isNeonMode) {
            helpText.setText("❤魅力模式已开启：屏幕左红右紫。\n建议横屏使用，预览窗会自动居中。\n点右上角按钮可切换预览窗大小。");
        } else if (intensity < 35) {
            helpText.setText("当前补光偏弱，建议提升强度到 70% 以上。\n拖动预览框到前摄附近，效果更稳定。");
        } else if (intensity < 70) {
            helpText.setText("当前补光中等，可继续微调粉色系色调让肤色更自然。\n点预览框右上角按钮可切换大小。");
        } else {
            helpText.setText("当前补光较强，适合暗光自拍。\n若过曝可轻微降低强度。\n点右上角按钮可切换大小，拖动可调整位置。");
        }
    }

    private float mapHueToPinkRange(int progress) {
        float mapped = 330f + (progress / 100f) * 50f;
        if (mapped >= 360f) {
            mapped -= 360f;
        }
        return mapped;
    }

    private void applyCharmSplitBackground(float normalizedIntensity) {
        float value = 0.35f + 0.65f * normalizedIntensity;
        int leftRed = Color.HSVToColor(new float[]{350f, 0.85f, value});
        int rightPurple = Color.HSVToColor(new float[]{285f, 0.80f, value});
        charmModeBackground.setVisibility(View.VISIBLE);
        charmLeftPanel.setBackgroundColor(leftRed);
        charmRightPanel.setBackgroundColor(rightPurple);
    }

    private void applyCharmModeLayout(boolean enabled) {
        if (enabled) {
            centerPreviewContainer();
            Toast.makeText(this, "❤魅力模式：建议横屏使用，左右分屏补光更明显", Toast.LENGTH_SHORT).show();
        } else {
            positionPreviewTopCenter();
        }
    }

    private void centerPreviewContainer() {
        previewContainer.post(() -> {
            View parent = (View) previewContainer.getParent();
            if (parent == null) return;
            previewContainer.setX((parent.getWidth() - previewContainer.getWidth()) / 2f);
            previewContainer.setY((parent.getHeight() - previewContainer.getHeight()) / 2f);
        });
    }

    private void positionPreviewTopCenter() {
        previewContainer.post(() -> {
            View parent = (View) previewContainer.getParent();
            if (parent == null) return;
            previewContainer.setX((parent.getWidth() - previewContainer.getWidth()) / 2f);
            previewContainer.setY(dpToPx(28));
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        startPreviewSafely();
    }

    private void startPreviewSafely() {
        if (camera == null || surfaceHolder == null || surfaceHolder.getSurface() == null) return;
        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.setDisplayOrientation(90);
            camera.startPreview();
        } catch (IOException e) {
            Toast.makeText(this, "预览失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (holder.getSurface() == null) return;
        try {
            if (camera != null) {
                camera.stopPreview();
            }
            startPreviewSafely();
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
        setWindowBrightnessMax();
        if (isCameraStarted && camera == null) {
            startCamera();
        }
        startPreviewSafely();
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
