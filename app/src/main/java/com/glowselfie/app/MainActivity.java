package com.glowselfie.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
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
    private View controlsPanel;
    private View actionBar;
    private SeekBar intensitySlider;
    private SeekBar hueSlider;
    private TextView intensityValueText;
    private TextView hueValueText;
    private TextView helpText;
    private Button openGalleryButton;
    private Button resizePreviewButton;
    private Button toggleSettingsButton;
    private Switch neonModeSwitch;
    private TextView charmDividerLabel;
    private SeekBar charmDividerSlider;
    private View charmModeBackground;
    private View charmLeftPanel;
    private View charmRightPanel;
    private View charmDivider;
    private boolean isCameraStarted = false;
    private Uri lastPhotoUri;
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
        controlsPanel = findViewById(R.id.controlsPanel);
        actionBar = findViewById(R.id.actionBar);
        Button captureButton = findViewById(R.id.captureButton);
        openGalleryButton = findViewById(R.id.openGalleryButton);
        intensitySlider = findViewById(R.id.intensitySlider);
        hueSlider = findViewById(R.id.hueSlider);
        intensityValueText = findViewById(R.id.intensityValueText);
        hueValueText = findViewById(R.id.hueValueText);
        helpText = findViewById(R.id.helpText);
        resizePreviewButton = findViewById(R.id.resizePreviewButton);
        toggleSettingsButton = findViewById(R.id.toggleSettingsButton);
        neonModeSwitch = findViewById(R.id.neonModeSwitch);
        ImageButton infoButton = findViewById(R.id.infoButton);
        charmDividerLabel = findViewById(R.id.charmDividerLabel);
        charmDividerSlider = findViewById(R.id.charmDividerSlider);
        charmModeBackground = findViewById(R.id.charmModeBackground);
        charmLeftPanel = findViewById(R.id.charmLeftPanel);
        charmRightPanel = findViewById(R.id.charmRightPanel);
        charmDivider = findViewById(R.id.charmDivider);

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
        toggleSettingsButton.setOnClickListener(v -> toggleSettingsPanel());
        infoButton.setOnClickListener(v -> showInfoDialog());
        resizePreviewButton.setOnClickListener(v -> {
            previewSizeStepIndex = (previewSizeStepIndex + 1) % previewWidthDpSteps.length;
            applyPreviewContainerSize(currentPreviewAspect);
            Toast.makeText(this, "预览框大小已切换", Toast.LENGTH_SHORT).show();
        });
        neonModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isNeonMode = isChecked;
            hueSlider.setEnabled(!isChecked);
            charmDividerSlider.setEnabled(isChecked);
            charmDividerLabel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            charmDividerSlider.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            applyCharmModeLayout(isChecked);
            updateBackgroundColor(intensitySlider.getProgress(), hueSlider.getProgress());
        });
        charmDividerSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isNeonMode) {
                    updateBackgroundColor(intensitySlider.getProgress(), hueSlider.getProgress());
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
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

        updateSettingsToggleText();
        charmDividerSlider.setEnabled(false);
        charmDividerLabel.setVisibility(View.GONE);
        charmDividerSlider.setVisibility(View.GONE);

        actionBar.post(this::adjustControlsPanelBottomMargin);
    }

    private void showInfoDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_info, null, false);
        TextView versionText = dialogView.findViewById(R.id.versionText);
        versionText.setText(getString(R.string.version_label, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void toggleSettingsPanel() {
        if (controlsPanel.getVisibility() == View.VISIBLE) {
            controlsPanel.setVisibility(View.GONE);
        } else {
            adjustControlsPanelBottomMargin();
            controlsPanel.setVisibility(View.VISIBLE);
        }
        updateSettingsToggleText();
    }

    private void adjustControlsPanelBottomMargin() {
        if (controlsPanel == null || actionBar == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) controlsPanel.getLayoutParams();
        int safeGap = dpToPx(16);
        lp.bottomMargin = actionBar.getHeight() + safeGap;
        controlsPanel.setLayoutParams(lp);
    }

    private void updateSettingsToggleText() {
        if (controlsPanel.getVisibility() == View.VISIBLE) {
            toggleSettingsButton.setText(R.string.hide_settings);
        } else {
            toggleSettingsButton.setText(R.string.show_settings);
        }
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
            try {
                Uri savedUri = savePhotoToGallery(data);
                if (savedUri != null) {
                    lastPhotoUri = savedUri;
                    openGalleryButton.setEnabled(true);
                    Toast.makeText(MainActivity.this,
                            "照片已保存到系统相册", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this,
                            "保存失败：无法写入相册", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(MainActivity.this,
                        "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            camera.startPreview();
        });
    }

    private Uri savePhotoToGallery(byte[] data) throws IOException {
        String fileName = new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(new Date()) + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "GlowSelfie");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        } else {
            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File outputDir = new File(picturesDir, "GlowSelfie");
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                throw new IOException("无法创建相册目录");
            }
            File outputFile = new File(outputDir, fileName);
            values.put(MediaStore.Images.Media.DATA, outputFile.getAbsolutePath());
        }

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            return null;
        }

        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) {
                throw new IOException("无法打开输出流");
            }
            os.write(data);
            os.flush();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues publish = new ContentValues();
            publish.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, publish, null, null);
        }

        return uri;
    }

    private void openLastPhoto() {
        if (lastPhotoUri == null) {
            Toast.makeText(this, "还没有可查看的照片", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(lastPhotoUri, "image/*");
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
        String toneLabel = getToneLabel(hue);

        if (isNeonMode) {
            applyCharmSplitBackground(normalizedIntensity);
        } else {
            charmModeBackground.setVisibility(View.GONE);
            int baseColor = pickFillLightBaseColor(hue);
            int adjustedColor = applyIntensityToColor(baseColor, normalizedIntensity);
            getWindow().getDecorView().setBackgroundColor(adjustedColor);
        }

        intensityValueText.setText(getString(R.string.intensity_value, intensity));
        hueValueText.setText(getString(R.string.hue_value, toneLabel));

        if (isNeonMode) {
            helpText.setText("❤魅力模式已开启：屏幕左红右紫。\n建议横屏使用，预览窗会自动居中。\n点右上角按钮可切换预览窗大小。");
        } else if (intensity < 35) {
            helpText.setText("当前补光偏弱，建议提升强度到 70% 以上。\n色调滑杆：左冷白，中粉润，右红润。");
        } else if (intensity < 70) {
            helpText.setText("当前补光中等，可继续微调：左冷白显白，中间粉润，右侧红润。\n点预览框右上角按钮可切换大小。");
        } else {
            helpText.setText("当前补光较强，适合暗光自拍。\n若过曝可轻微降低强度。\n点右上角按钮可切换大小，拖动可调整位置。");
        }
    }

    private int pickFillLightBaseColor(int progress) {
        int coolWhiteBlue = Color.rgb(196, 230, 255);
        int softPink = Color.rgb(255, 179, 214);
        int warmRedPink = Color.rgb(255, 120, 135);

        if (progress <= 50) {
            float t = progress / 50f;
            return blendColor(coolWhiteBlue, softPink, t);
        }
        float t = (progress - 50f) / 50f;
        return blendColor(softPink, warmRedPink, t);
    }

    private void applyCharmSplitBackground(float normalizedIntensity) {
        int leftRed = applyIntensityToColor(Color.rgb(255, 0, 20), normalizedIntensity);
        int rightPurple = applyIntensityToColor(Color.rgb(140, 0, 255), normalizedIntensity);
        charmModeBackground.setVisibility(View.VISIBLE);
        charmLeftPanel.setBackgroundColor(leftRed);
        charmRightPanel.setBackgroundColor(rightPurple);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) charmDivider.getLayoutParams();
        lp.width = dpToPx(8 + (int) (charmDividerSlider.getProgress() * 0.92f));
        charmDivider.setLayoutParams(lp);
    }

    private int applyIntensityToColor(int baseColor, float normalizedIntensity) {
        float factor = 0.45f + 0.55f * normalizedIntensity;
        int r = Math.round(Color.red(baseColor) * factor);
        int g = Math.round(Color.green(baseColor) * factor);
        int b = Math.round(Color.blue(baseColor) * factor);
        return Color.rgb(clampColor(r), clampColor(g), clampColor(b));
    }

    private int blendColor(int start, int end, float t) {
        int r = Math.round(Color.red(start) + (Color.red(end) - Color.red(start)) * t);
        int g = Math.round(Color.green(start) + (Color.green(end) - Color.green(start)) * t);
        int b = Math.round(Color.blue(start) + (Color.blue(end) - Color.blue(start)) * t);
        return Color.rgb(clampColor(r), clampColor(g), clampColor(b));
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private String getToneLabel(int progress) {
        if (progress < 33) {
            return getString(R.string.tone_cool);
        }
        if (progress > 66) {
            return getString(R.string.tone_warm);
        }
        return getString(R.string.tone_soft);
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
        actionBar.post(this::adjustControlsPanelBottomMargin);
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
