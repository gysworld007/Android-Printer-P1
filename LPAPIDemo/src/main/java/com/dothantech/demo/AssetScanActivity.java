package com.dothantech.demo;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.journeyapps.barcodescanner.Size;
import com.journeyapps.barcodescanner.camera.CameraParametersCallback;
import com.journeyapps.barcodescanner.camera.CameraSettings;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AssetScanActivity extends Activity implements DecoratedBarcodeView.TorchListener {

    public static final String EXTRA_SCAN_RESULT = "asset_scan_result";

    private DecoratedBarcodeView barcodeView;
    private Button torchButton;
    private Button smallCodeButton;
    private TextView zoomText;
    private TextView tipText;
    private boolean resultReturned;
    private boolean smallCodeMode;
    private int zoomLevel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        barcodeView = new DecoratedBarcodeView(this);
        CameraSettings cameraSettings = new CameraSettings();
        cameraSettings.setRequestedCameraId(0);
        cameraSettings.setAutoFocusEnabled(true);
        cameraSettings.setContinuousFocusEnabled(true);
        cameraSettings.setFocusMode(CameraSettings.FocusMode.CONTINUOUS);
        cameraSettings.setBarcodeSceneModeEnabled(true);
        cameraSettings.setExposureEnabled(true);
        cameraSettings.setMeteringEnabled(true);
        barcodeView.getBarcodeView().setCameraSettings(cameraSettings);
        barcodeView.getBarcodeView().setMarginFraction(0.08);
        barcodeView.getBarcodeView().setFramingRectSize(new Size(900, 520));
        List<BarcodeFormat> formats = Arrays.asList(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.ITF,
                BarcodeFormat.DATA_MATRIX
        );
        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats, hints, "UTF-8", 0));
        barcodeView.setTorchListener(this);
        barcodeView.setStatusText("请扫描资产二维码或条形码");

        FrameLayout root = new FrameLayout(this);
        root.addView(barcodeView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

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
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            torchButton.setEnabled(false);
            torchButton.setText("无补光灯");
        }
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

        FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.RIGHT
        );
        controlParams.setMargins(20, 20, 20, 220);
        root.addView(controlPanel, controlParams);

        tipText = new TextView(this);
        tipText.setText("小码可点小码模式或放大，让二维码/条形码在框内清晰可见");
        tipText.setTextColor(0xffffffff);
        tipText.setTextSize(16);
        tipText.setGravity(Gravity.CENTER);
        tipText.setPadding(20, 12, 20, 12);
        tipText.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams tipParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        tipParams.setMargins(20, 20, 20, 80);
        root.addView(tipText, tipParams);

        setContentView(root);
        barcodeView.decodeContinuous(callback);
    }

    private void toggleTorch() {
        if ("关闭补光灯".contentEquals(torchButton.getText())) {
            barcodeView.setTorchOff();
        } else {
            barcodeView.setTorchOn();
        }
    }

    private void toggleSmallCodeMode() {
        smallCodeMode = !smallCodeMode;
        if (smallCodeMode) {
            barcodeView.getBarcodeView().setFramingRectSize(new Size(700, 700));
            smallCodeButton.setText("普通模式");
            tipText.setText("小码模式：请把二维码转正并填满方框，必要时放大和打开补光灯");
            changeZoom(3);
        } else {
            barcodeView.getBarcodeView().setFramingRectSize(new Size(900, 520));
            smallCodeButton.setText("小码模式");
            tipText.setText("小码可点小码模式或放大，让二维码/条形码在框内清晰可见");
            changeZoom(-zoomLevel);
        }
    }

    private void changeZoom(int delta) {
        zoomLevel += delta;
        if (zoomLevel < 0) {
            zoomLevel = 0;
        }
        barcodeView.changeCameraParameters(new CameraParametersCallback() {
            @Override
            public Camera.Parameters changeCameraParameters(Camera.Parameters parameters) {
                if (parameters == null || !parameters.isZoomSupported()) {
                    zoomLevel = 0;
                    updateZoomText(0, 0);
                    return parameters;
                }
                int maxZoom = parameters.getMaxZoom();
                if (zoomLevel > maxZoom) {
                    zoomLevel = maxZoom;
                }
                parameters.setZoom(zoomLevel);
                updateZoomText(zoomLevel, maxZoom);
                return parameters;
            }
        });
    }

    private void updateZoomText(final int zoom, final int maxZoom) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (maxZoom <= 0) {
                    zoomText.setText("不支持缩放");
                } else {
                    zoomText.setText(String.format("%.1fx", 1f + (zoom * 3f / maxZoom)));
                }
            }
        });
    }

    @Override
    public void onTorchOn() {
        torchButton.setText("关闭补光灯");
    }

    @Override
    public void onTorchOff() {
        torchButton.setText("打开补光灯");
    }

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (resultReturned || result == null || result.getText() == null || result.getText().length() == 0) {
                return;
            }
            resultReturned = true;
            Intent data = new Intent();
            data.putExtra(EXTRA_SCAN_RESULT, result.getText());
            setResult(RESULT_OK, data);
            finish();
        }

        @Override
        public void possibleResultPoints(List<ResultPoint> resultPoints) {
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        barcodeView.resume();
    }

    @Override
    protected void onPause() {
        barcodeView.setTorchOff();
        barcodeView.pause();
        super.onPause();
    }
}
