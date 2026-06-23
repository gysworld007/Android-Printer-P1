package com.dothantech.demo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;

public class AssetMlkitScanActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private static final int REQUEST_CAMERA_PERMISSION = 3001;
    private static final int REQUEST_ZXING_SCAN = 3002;

    private SurfaceView previewView;
    private TextView tipText;
    private TextView zoomText;
    private Button torchButton;
    private Button smallCodeButton;
    private Camera camera;
    private BarcodeScanner scanner;
    private boolean surfaceReady;
    private boolean processingFrame;
    private boolean resultReturned;
    private boolean torchOn;
    private boolean smallCodeMode;
    private int zoomLevel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createScanner();
        buildContentView();
        if (hasCameraPermission()) {
            startCameraIfReady();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private void buildContentView() {
        FrameLayout root = new FrameLayout(this);
        previewView = new SurfaceView(this);
        previewView.getHolder().addCallback(this);
        root.addView(previewView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("Google ML Kit 扫码");
        title.setTextColor(0xffffffff);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        title.setPadding(20, 18, 20, 18);
        title.setBackgroundColor(0x99000000);
        root.addView(title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        LinearLayout controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.VERTICAL);
        controlPanel.setGravity(Gravity.CENTER);
        controlPanel.setBackgroundColor(0x66000000);

        torchButton = new Button(this);
        torchButton.setText("打开补光灯");
        torchButton.setTextColor(0xffffffff);
        torchButton.setBackgroundColor(0x993bb3c3);
        torchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTorch();
            }
        });
        controlPanel.addView(torchButton);

        smallCodeButton = new Button(this);
        smallCodeButton.setText("小码模式");
        smallCodeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSmallCodeMode();
            }
        });
        controlPanel.addView(smallCodeButton);

        Button zxingButton = new Button(this);
        zxingButton.setText("旧版扫码");
        zxingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(AssetMlkitScanActivity.this, AssetScanActivity.class), REQUEST_ZXING_SCAN);
            }
        });
        controlPanel.addView(zxingButton);

        LinearLayout zoomPanel = new LinearLayout(this);
        zoomPanel.setOrientation(LinearLayout.HORIZONTAL);
        zoomPanel.setGravity(Gravity.CENTER);
        Button zoomOutButton = new Button(this);
        zoomOutButton.setText("缩小");
        zoomOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeZoom(-1);
            }
        });
        zoomText = new TextView(this);
        zoomText.setText("1.0x");
        zoomText.setTextColor(0xffffffff);
        zoomText.setTextSize(16);
        zoomText.setGravity(Gravity.CENTER);
        zoomText.setPadding(20, 0, 20, 0);
        Button zoomInButton = new Button(this);
        zoomInButton.setText("放大");
        zoomInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeZoom(1);
            }
        });
        zoomPanel.addView(zoomOutButton);
        zoomPanel.addView(zoomText);
        zoomPanel.addView(zoomInButton);
        controlPanel.addView(zoomPanel);

        FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
        controlParams.setMargins(20, 20, 20, 220);
        root.addView(controlPanel, controlParams);

        tipText = new TextView(this);
        tipText.setText("请将二维码/条形码放入画面中央，小码可点小码模式并放大");
        tipText.setTextColor(0xffffffff);
        tipText.setTextSize(16);
        tipText.setGravity(Gravity.CENTER);
        tipText.setPadding(20, 12, 20, 12);
        tipText.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams tipParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        tipParams.setMargins(20, 20, 20, 80);
        root.addView(tipText, tipParams);

        setContentView(root);
    }

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCameraIfReady();
        } else {
            Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void createScanner() {
        if (scanner != null) {
            scanner.close();
        }
        BarcodeScannerOptions.Builder builder = new BarcodeScannerOptions.Builder();
        if (smallCodeMode) {
            builder.setBarcodeFormats(Barcode.FORMAT_QR_CODE);
        } else {
            builder.setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_CODE_93,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_ITF,
                    Barcode.FORMAT_DATA_MATRIX
            );
        }
        scanner = BarcodeScanning.getClient(builder.build());
    }

    private void startCameraIfReady() {
        if (!surfaceReady || camera != null || resultReturned) {
            return;
        }
        try {
            camera = Camera.open();
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = choosePreviewSize(parameters.getSupportedPreviewSizes());
            if (size != null) {
                parameters.setPreviewSize(size.width, size.height);
            }
            parameters.setPreviewFormat(ImageFormat.NV21);
            setFocusMode(parameters);
            camera.setParameters(parameters);
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(previewView.getHolder());
            camera.setPreviewCallback(this);
            camera.startPreview();
            updateFlashButton();
            changeZoom(0);
        } catch (Exception e) {
            Toast.makeText(this, "打开相机失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            stopCamera();
        }
    }

    private Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
        if (sizes == null || sizes.size() == 0) {
            return null;
        }
        Camera.Size best = null;
        int bestArea = 0;
        int maxArea = 1920 * 1080;
        for (Camera.Size size : sizes) {
            int area = size.width * size.height;
            if (area <= maxArea && area > bestArea) {
                best = size;
                bestArea = area;
            }
        }
        if (best != null) {
            return best;
        }
        for (Camera.Size size : sizes) {
            int area = size.width * size.height;
            if (area > bestArea) {
                best = size;
                bestArea = area;
            }
        }
        return best;
    }

    private void setFocusMode(Camera.Parameters parameters) {
        List<String> modes = parameters.getSupportedFocusModes();
        if (modes == null) {
            return;
        }
        if (modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else if (modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        } else if (modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
    }

    private void toggleTorch() {
        if (camera == null) {
            return;
        }
        try {
            Camera.Parameters parameters = camera.getParameters();
            List<String> modes = parameters.getSupportedFlashModes();
            if (modes == null || !modes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                Toast.makeText(this, "当前设备不支持补光灯", Toast.LENGTH_SHORT).show();
                return;
            }
            torchOn = !torchOn;
            parameters.setFlashMode(torchOn ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(parameters);
            updateFlashButton();
        } catch (Exception e) {
            Toast.makeText(this, "补光灯切换失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateFlashButton() {
        if (torchButton == null) {
            return;
        }
        if (camera == null) {
            torchButton.setText("打开补光灯");
            return;
        }
        Camera.Parameters parameters = camera.getParameters();
        List<String> modes = parameters.getSupportedFlashModes();
        if (modes == null || !modes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
            torchButton.setEnabled(false);
            torchButton.setText("无补光灯");
        } else {
            torchButton.setEnabled(true);
            torchButton.setText(torchOn ? "关闭补光灯" : "打开补光灯");
        }
    }

    private void toggleSmallCodeMode() {
        smallCodeMode = !smallCodeMode;
        createScanner();
        if (smallCodeMode) {
            smallCodeButton.setText("普通模式");
            tipText.setText("ML Kit 小码模式：仅识别二维码，请转正、放大并保持清晰");
            changeZoom(3);
        } else {
            smallCodeButton.setText("小码模式");
            tipText.setText("请将二维码/条形码放入画面中央，小码可点小码模式并放大");
            changeZoom(-zoomLevel);
        }
    }

    private void changeZoom(int delta) {
        if (camera == null) {
            return;
        }
        try {
            zoomLevel += delta;
            if (zoomLevel < 0) {
                zoomLevel = 0;
            }
            Camera.Parameters parameters = camera.getParameters();
            if (!parameters.isZoomSupported()) {
                zoomLevel = 0;
                updateZoomText(0, 0);
                return;
            }
            int maxZoom = parameters.getMaxZoom();
            if (zoomLevel > maxZoom) {
                zoomLevel = maxZoom;
            }
            parameters.setZoom(zoomLevel);
            camera.setParameters(parameters);
            updateZoomText(zoomLevel, maxZoom);
        } catch (Exception e) {
            updateZoomText(0, 0);
        }
    }

    private void updateZoomText(int zoom, int maxZoom) {
        if (zoomText == null) {
            return;
        }
        if (maxZoom <= 0) {
            zoomText.setText("不支持缩放");
        } else {
            zoomText.setText(String.format("%.1fx", 1f + (zoom * 3f / maxZoom)));
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (processingFrame || resultReturned || scanner == null || data == null || camera == null) {
            return;
        }
        Camera.Size size;
        try {
            size = camera.getParameters().getPreviewSize();
        } catch (Exception e) {
            return;
        }
        processingFrame = true;
        byte[] frameData = data.clone();
        InputImage image = InputImage.fromByteArray(frameData, size.width, size.height, 90, InputImage.IMAGE_FORMAT_NV21);
        scanner.process(image)
                .addOnSuccessListener(new OnSuccessListener<List<Barcode>>() {
                    @Override
                    public void onSuccess(List<Barcode> barcodes) {
                        handleBarcodes(barcodes);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                    }
                })
                .addOnCompleteListener(new OnCompleteListener<List<Barcode>>() {
                    @Override
                    public void onComplete(Task<List<Barcode>> task) {
                        processingFrame = false;
                    }
                });
    }

    private void handleBarcodes(List<Barcode> barcodes) {
        if (barcodes == null || resultReturned) {
            return;
        }
        for (Barcode barcode : barcodes) {
            String value = barcode.getRawValue();
            if (!TextUtils.isEmpty(value)) {
                returnResult(value);
                return;
            }
        }
    }

    private void returnResult(String value) {
        resultReturned = true;
        Intent data = new Intent();
        data.putExtra(AssetScanActivity.EXTRA_SCAN_RESULT, value);
        setResult(RESULT_OK, data);
        finish();
    }

    private void stopCamera() {
        if (camera != null) {
            try {
                camera.setPreviewCallback(null);
                camera.stopPreview();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                camera.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            camera = null;
        }
        torchOn = false;
        updateFlashButton();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ZXING_SCAN && resultCode == RESULT_OK && data != null) {
            String value = data.getStringExtra(AssetScanActivity.EXTRA_SCAN_RESULT);
            if (!TextUtils.isEmpty(value)) {
                returnResult(value);
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        startCameraIfReady();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        stopCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasCameraPermission()) {
            startCameraIfReady();
        }
    }

    @Override
    protected void onPause() {
        stopCamera();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (scanner != null) {
            scanner.close();
        }
        super.onDestroy();
    }
}
