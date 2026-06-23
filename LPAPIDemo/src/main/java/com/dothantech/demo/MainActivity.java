package com.dothantech.demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.dothantech.lpapi.LPAPI;
import com.dothantech.lpapi.LPAPI.BarcodeType;
import com.dothantech.printer.IDzPrinter;
import com.dothantech.printer.IDzPrinter.PrintParamName;
import com.dothantech.printer.IDzPrinter.PrintProgress;
import com.dothantech.printer.IDzPrinter.AddressType;
import com.dothantech.printer.IDzPrinter.PrinterAddress;
import com.dothantech.printer.IDzPrinter.PrinterState;
import com.dothantech.printer.IDzPrinter.ProgressInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@SuppressLint("InflateParams")
public class MainActivity extends Activity {

    private static final int REQUEST_SCAN_SN = 1001;
    private static final int REQUEST_EXPORT_ASSETS = 1002;
    private static final int REQUEST_ASSET_MANAGER = 1003;
    private static final String PREFS_NAME = "asset_label_prefs";
    private static final String PREF_PRINTER_MAC = "printer_mac";
    private static final String PREF_PRINTER_NAME = "printer_name";
    private static final String PREF_PRINTER_TYPE = "printer_type";
    private static final String PREFERRED_PRINTER_NAME = "P1";

    private LPAPI api;
    private final Handler mHandler = new Handler();

    private Button btnConnectDevice;
    private Button btnGapType;
    private Button btnGapLength;
    private Button btnPrintDensity;
    private Button btnPrintSpeed;
    private Button btnAssetLabels;
    private Button btnExportAssets;
    private Button btnScanAsset;
    private EditText et1;
    private EditText et2;

    private int gapType = -1;
    private int gapLength = 3;
    private int printDensity = -1;
    private int printSpeed = -1;
    private int printCopiesNum = 1;
    private int currentPrintCopiedNum = 0;

    private String defaultText1 = "上海道臻信息技术有限公司\nDzPrinterDemo";
    private String defaultText2 = "上海道臻信息技术有限公司\nDzPrinterDemo";
    private String default1dBarcode = "1234567890";
    private String default2dBarcode = "http://www.dothantech.com/";

    private final String[] printDensityList = new String[]{"随打印机设置", "1 (最淡)", "2", "3 (较淡)", "4", "5", "6 (正常)", "7", "8", "9", "10", "11", "12(较深)", "13", "14", "15", "16", "17", "18", "19", "20(最深)"};
    private final String[] printSpeedList = new String[]{"随打印机设置", "1 (最慢)", "2 (较慢)", "3 (正常)", "4 (较快)", "5 (最快)"};
    private final String[] gapTypeList = new String[]{"随打印机设置", "连续纸", "定位孔", "间隙纸", "黑标纸"};
    private final int[] bitmapOrientations = new int[]{0, 90, 0, 90};

    private final List<Bitmap> printBitmaps = new ArrayList<>();
    private final List<AssetLabel> assetLabels = new ArrayList<>();
    private AlertDialog stateAlertDialog;
    private DeviceListAdapter printerAdapter;
    private List<PrinterAddress> discoveredPrinterList;
    private boolean autoConnectingPrinter;
    private EditText pendingScanSnEditText;
    private AssetLabel pendingScanEditingAsset;

    private static String safeText(String value) {
        return AssetStore.safeText(value);
    }

    private final LPAPI.Callback mCallback = new LPAPI.Callback() {
        @Override
        public void onStateChange(final PrinterAddress printer, PrinterState state) {
            if (state == PrinterState.Connected || state == PrinterState.Connected2) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onPrinterConnected(printer);
                    }
                });
            } else if (state == PrinterState.Disconnected) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onPrinterDisconnected();
                    }
                });
            }
        }

        @Override
        public void onProgressInfo(ProgressInfo progressInfo, Object o) {
        }

        @Override
        public void onPrinterDiscovery(PrinterAddress printerAddress, Object o) {
            onPrinterDiscovered(printerAddress);
        }

        @Override
        public void onPrintProgress(PrinterAddress address, IDzPrinter.PrintData bitmapData, PrintProgress progress, Object addiInfo) {
            if (progress == PrintProgress.Success) {
                if (printCopiesNum > 1) {
                    currentPrintCopiedNum++;
                    if (currentPrintCopiedNum < printCopiesNum) {
                        printMultipleLabelOnClick();
                        return;
                    }
                    printCopiesNum = 1;
                    currentPrintCopiedNum = 0;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onPrintSuccess();
                    }
                });
            } else if (progress == PrintProgress.Failed) {
                printCopiesNum = 1;
                currentPrintCopiedNum = 0;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onPrintFailed();
                    }
                });
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        loadAssetLabels();
        initialView();
        api = LPAPI.Factory.createInstance(mCallback);
        requestPermission();
        scheduleAutoConnectPrinter();
    }

    @Override
    protected void onDestroy() {
        if (api != null) {
            api.quit();
        }
        super.onDestroy();
    }

    private void loadAssetLabels() {
        assetLabels.clear();
        assetLabels.addAll(AssetStore.load(MainActivity.this));
    }

    private void saveAssetLabels() {
        AssetStore.save(MainActivity.this, assetLabels);
    }

    private List<AssetLabel> getActiveAssets() {
        return AssetStore.activeAssets(assetLabels);
    }

    private List<AssetLabel> getArchivedAssets() {
        return AssetStore.archivedAssets(assetLabels);
    }

    private AssetLabel findActiveAssetBySn(String sn, AssetLabel exceptAsset) {
        return AssetStore.findActiveBySn(assetLabels, sn, exceptAsset);
    }

    private void archiveAsset(AssetLabel asset, String reason) {
        asset.archived = true;
        asset.archivedAt = System.currentTimeMillis();
        asset.archiveReason = reason;
        asset.updatedAt = asset.archivedAt;
        AssetStore.addEvent(asset, "归还", reason);
        saveAssetLabels();
    }

    private boolean restoreAsset(AssetLabel asset) {
        if (findActiveAssetBySn(asset.sn, asset) != null) {
            Toast.makeText(MainActivity.this, "在用资产中已存在相同SN，无法恢复", Toast.LENGTH_LONG).show();
            return false;
        }
        asset.archived = false;
        asset.archivedAt = 0;
        asset.archiveReason = "";
        asset.updatedAt = System.currentTimeMillis();
        AssetStore.addEvent(asset, "恢复", "从历史记录恢复");
        saveAssetLabels();
        return true;
    }

    private static String normalizeScannedSn(String value) {
        return AssetStore.normalizeScannedSn(value);
    }

    private static String formatTime(long time) {
        return AssetStore.formatTime(time);
    }

    private String generateAssetCode() {
        String datePart = new SimpleDateFormat("yyMMdd", Locale.US).format(new Date());
        String prefix = "A" + datePart;
        int maxIndex = 0;
        for (AssetLabel asset : assetLabels) {
            if (asset.code != null && asset.code.startsWith(prefix) && asset.code.length() >= prefix.length() + 3) {
                try {
                    int index = Integer.parseInt(asset.code.substring(prefix.length()));
                    if (index > maxIndex) {
                        maxIndex = index;
                    }
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }
        return prefix + String.format(Locale.US, "%03d", maxIndex + 1);
    }

    private Bundle getPrintParam(int copies, int orientation) {
        Bundle param = new Bundle();
        if (gapType >= 0) {
            param.putInt(PrintParamName.GAP_TYPE, gapType);
        }
        if (gapLength >= 0) {
            param.putInt(PrintParamName.GAP_LENGTH, gapLength);
        }
        if (printDensity >= 0) {
            param.putInt(PrintParamName.PRINT_DENSITY, printDensity);
        }
        if (printSpeed >= 0) {
            param.putInt(PrintParamName.PRINT_SPEED, printSpeed);
        }
        if (orientation != 0) {
            param.putInt(PrintParamName.PRINT_DIRECTION, orientation);
        }
        if (copies > 1) {
            param.putInt(PrintParamName.PRINT_COPIES, copies);
        }
        return param;
    }

    private boolean isPrinterConnected() {
        PrinterState state = api.getPrinterState();
        if (state == null || state == PrinterState.Disconnected) {
            Toast.makeText(MainActivity.this, "打印机未连接，请先连接打印机！", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (state == PrinterState.Connecting) {
            Toast.makeText(MainActivity.this, "正在连接打印机，请稍候！", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private boolean printText(String text) {
        api.startJob(48, 50, 0);
        api.drawText(text, 4, 5, 40, 40, 4);
        return api.commitJob();
    }

    private List<Bitmap> printText1DBarcode(String text, String oneDBarcode) {
        api.startJob(48, 48, 90);
        api.drawText(text, 4, 4, 40, 20, 4);
        api.setItemOrientation(180);
        api.draw1DBarcode(oneDBarcode, BarcodeType.AUTO, 4, 25, 40, 15, 3);
        api.endJob();
        return api.getJobPages();
    }

    private boolean print2dBarcode(String twoDBarcode, Bundle param) {
        api.startJob(48, 50, 0);
        api.draw2DQRCode(twoDBarcode, 9, 10, 30);
        return api.commitJobWithParam(param);
    }

    private boolean printBitmap(Bitmap bitmap, Bundle param) {
        return api.printBitmap(bitmap, param);
    }

    private boolean printAssetLabel(AssetLabel asset, int copies) {
        double labelWidth = 30;
        double labelHeight = 15;
        double margin = 1;
        double qrSize = 11;
        double textX = margin + qrSize + 1;
        double textWidth = labelWidth - textX - margin;

        api.startJob(labelWidth, labelHeight, 0);
        api.draw2DQRCode(safeText(asset.code) + "|" + safeText(asset.sn), margin, 2, qrSize);
        api.setItemHorizontalAlignment(0);
        api.setItemVerticalAlignment(1);
        api.drawTextRegular(safeText(asset.code), textX, 1, textWidth, 3.5, 2.4, 1);
        api.drawTextRegular(safeText(asset.name), textX, 4.8, textWidth, 3, 2.2, 1);
        api.drawTextRegular("SN:" + safeText(asset.sn), textX, 8.1, textWidth, 3, 2.0, 1);
        api.drawTextRegular(safeText(asset.user), textX, 11.2, textWidth, 2.6, 1.8, 1);
        return api.commitJobWithParam(getPrintParam(copies, 0));
    }

    private void initialView() {
        btnConnectDevice = findViewById(R.id.btn_printer);
        btnGapType = findViewById(R.id.btn_gaptype);
        btnGapLength = findViewById(R.id.btn_gaplength);
        btnPrintDensity = findViewById(R.id.btn_printdensity);
        btnPrintSpeed = findViewById(R.id.btn_printspeed);
        btnAssetLabels = findViewById(R.id.btn_assetlabels);
        btnExportAssets = findViewById(R.id.btn_exportassets);
        btnScanAsset = findViewById(R.id.btn_scanasset);

        btnConnectDevice.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { selectPrinterOnClick(); }});
        btnGapType.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { gapTypeOnClick(); }});
        btnGapLength.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { gapLengthOnClick(); }});
        btnPrintDensity.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { printDensityOnClick(); }});
        btnPrintSpeed.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { printSpeedOnClick(); }});
        btnAssetLabels.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startActivityForResult(new Intent(MainActivity.this, AssetManagerActivity.class), REQUEST_ASSET_MANAGER); }});
        btnScanAsset.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { scanAssetQuickly(); }});
        btnExportAssets.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { exportAssetsToExcel(); }});
        refreshPrintParamView();
        loadTestBitmaps();
    }

    private void loadTestBitmaps() {
        String[] testPicName = new String[]{"test1.png", "test2.png", "test3.png", "test4.png"};
        for (String name : testPicName) {
            try {
                InputStream is = getAssets().open(name);
                Bitmap bmp = BitmapFactory.decodeStream(is);
                if (bmp != null) {
                    printBitmaps.add(bmp);
                }
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void scheduleAutoConnectPrinter() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                autoConnectPrinter();
            }
        }, 1500);
    }

    private void autoConnectPrinter() {
        if (api == null || isPrinterConnected()) {
            return;
        }
        if (autoConnectLastPrinter()) {
            return;
        }
        autoConnectingPrinter = true;
        discoveredPrinterList = new ArrayList<>();
        btnConnectDevice.setText("正在自动搜索P1打印机...");
        api.discovery();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (autoConnectingPrinter) {
                    autoConnectingPrinter = false;
                    api.stopDiscovery();
                    if (!isPrinterConnected()) {
                        btnConnectDevice.setText("打印机：未连接，点击连接P1");
                    }
                }
            }
        }, 12000);
    }

    private boolean autoConnectLastPrinter() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String mac = preferences.getString(PREF_PRINTER_MAC, "");
        String typeName = preferences.getString(PREF_PRINTER_TYPE, "");
        String shownName = preferences.getString(PREF_PRINTER_NAME, "");
        if (TextUtils.isEmpty(mac) || TextUtils.isEmpty(typeName)) {
            return false;
        }
        try {
            AddressType addressType = AddressType.valueOf(typeName);
            PrinterAddress printer = new PrinterAddress(mac, shownName, addressType);
            if (api.openPrinterByAddress(printer)) {
                onPrinterConnecting(printer, false);
                return true;
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void saveLastPrinter(PrinterAddress printer) {
        if (printer == null || printer.addressType == null || TextUtils.isEmpty(printer.macAddress)) {
            return;
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(PREF_PRINTER_MAC, printer.macAddress)
                .putString(PREF_PRINTER_NAME, safeText(printer.shownName))
                .putString(PREF_PRINTER_TYPE, printer.addressType.name())
                .apply();
    }

    public void selectPrinterOnClick() {
        autoConnectingPrinter = false;
        api.stopDiscovery();
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            Toast.makeText(MainActivity.this, "当前设备不支持蓝牙！", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!btAdapter.isEnabled()) {
            Toast.makeText(MainActivity.this, "蓝牙适配器未开启！", Toast.LENGTH_SHORT).show();
            return;
        }
        api.discovery();
        discoveredPrinterList = new ArrayList<>();
        new AlertDialog.Builder(MainActivity.this).setTitle("选择搜索到的设备").setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                api.stopDiscovery();
            }
        }).setAdapter(printerAdapter = new DeviceListAdapter(MainActivity.this), new DeviceListItemClicker()).show();
    }

    public void gapTypeOnClick() {
        new AlertDialog.Builder(MainActivity.this).setTitle("设置间隔类型").setAdapter(new PrintParamAdapter(MainActivity.this, gapTypeList), new GapTypeItemClicker()).show();
    }

    public void gapLengthOnClick() {
        if (!isPrinterConnected()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("设置间隔长度（跳距）");
        builder.setView(initView("间隔长度：", String.valueOf(gapLength)));
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    gapLength = Integer.parseInt(et1.getText().toString());
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                btnGapLength.setText("间隔长度：\n" + gapLength);
                api.setPrintPageGapLength(gapLength);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    public void printDensityOnClick() {
        if (isPrinterConnected()) {
            new AlertDialog.Builder(MainActivity.this).setTitle("设置打印浓度").setAdapter(new PrintParamAdapter(MainActivity.this, printDensityList), new PrintDensityItemClicker()).show();
        }
    }

    public void printSpeedOnClick() {
        if (isPrinterConnected()) {
            new AlertDialog.Builder(MainActivity.this).setTitle("设置打印速度").setAdapter(new PrintParamAdapter(MainActivity.this, printSpeedList), new PrintSpeedItemClicker()).show();
        }
    }

    public void printTextOnClick() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("打印文本");
        builder.setView(initView("文本数据：", defaultText1));
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                defaultText1 = et1.getText().toString();
                if (isPrinterConnected()) {
                    if (printText(defaultText1)) onPrintStart(); else onPrintFailed();
                }
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    public void printText1DBarcodeOnClick() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("文本一维码");
        builder.setView(init1DBarcodeParamView(defaultText2, default1dBarcode));
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                defaultText2 = et1.getText().toString();
                default1dBarcode = et2.getText().toString();
                if (isPrinterConnected()) {
                    List<Bitmap> bitmaps = printText1DBarcode(defaultText2, default1dBarcode);
                    if (bitmaps != null && bitmaps.size() > 0) {
                        showPreviewBitmapDialog(bitmaps.get(0));
                    }
                }
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    public void print2DBarcodeOnClick() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("打印二维码");
        builder.setView(initView("二维码数据：", default2dBarcode));
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                default2dBarcode = et1.getText().toString();
                if (isPrinterConnected()) {
                    if (print2dBarcode(default2dBarcode, getPrintParam(1, 0))) onPrintStart(); else onPrintFailed();
                }
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    public void printBitmapOnClick() {
        new AlertDialog.Builder(MainActivity.this).setTitle("打印图片").setAdapter(new BitmapListAdapter(MainActivity.this, printBitmaps), new BitmapListItemClicker()).show();
    }

    private void exportAssetsToExcel() {
        String fileName = "资产台账_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".csv";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, REQUEST_EXPORT_ASSETS);
    }

    private void writeAssetsCsv(Uri uri) {
        OutputStream outputStream = null;
        try {
            outputStream = getContentResolver().openOutputStream(uri);
            if (outputStream == null) {
                Toast.makeText(MainActivity.this, "导出失败", Toast.LENGTH_LONG).show();
                return;
            }
            StringBuilder builder = new StringBuilder();
            builder.append("资产编码,SN,资产名称,使用人,购买日期,状态,创建时间,更新时间,归还时间,归还原因,备注\n");
            for (AssetLabel asset : assetLabels) {
                builder.append(csvCell(asset.code)).append(',')
                        .append(csvCell(asset.sn)).append(',')
                        .append(csvCell(asset.name)).append(',')
                        .append(csvCell(asset.user)).append(',')
                        .append(csvCell(asset.buyDate)).append(',')
                        .append(csvCell(asset.archived ? "已归还" : "在用")).append(',')
                        .append(csvCell(formatTime(asset.createdAt))).append(',')
                        .append(csvCell(formatTime(asset.updatedAt))).append(',')
                        .append(csvCell(formatTime(asset.archivedAt))).append(',')
                        .append(csvCell(asset.archiveReason)).append(',')
                        .append(csvCell(asset.remark)).append('\n');
            }
            outputStream.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            outputStream.write(builder.toString().getBytes("UTF-8"));
            Toast.makeText(MainActivity.this, "资产台账已导出", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(MainActivity.this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static String csvCell(String value) {
        String text = safeText(value);
        if (text.contains("\"") || text.contains(",") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    public void printLabelOnClick() {
        showAssetManagerDialog();
    }

    public void printMultipleLabelOnClick() {
        List<AssetLabel> activeAssets = getActiveAssets();
        if (activeAssets.size() == 0) {
            showAssetManagerDialog();
            return;
        }
        showPrintAssetCopiesDialog(activeAssets.get(0));
    }

    private void showPrintAssetCopiesDialog(final AssetLabel asset) {
        final EditText copiesEdit = new EditText(MainActivity.this);
        copiesEdit.setSingleLine(true);
        copiesEdit.setText("1");
        copiesEdit.setSelection(copiesEdit.getText().length());
        new AlertDialog.Builder(MainActivity.this).setTitle("打印标签份数").setView(copiesEdit).setPositiveButton("打印", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int copies;
                try {
                    copies = Integer.parseInt(copiesEdit.getText().toString().trim());
                } catch (NumberFormatException e) {
                    copies = 0;
                }
                if (copies < 1 || copies > 99) {
                    Toast.makeText(MainActivity.this, "请输入1-99之间的打印份数", Toast.LENGTH_LONG).show();
                    return;
                }
                if (isPrinterConnected()) {
                    if (printAssetLabel(asset, copies)) onPrintStart(); else onPrintFailed();
                }
            }
        }).setNegativeButton("取消", null).show();
    }

    private void showAssetManagerDialog() {
        final List<AssetLabel> activeAssets = getActiveAssets();
        String[] items = new String[activeAssets.size()];
        for (int i = 0; i < activeAssets.size(); i++) {
            items[i] = activeAssets.get(i).displayText();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("资产管理标签");
        if (items.length == 0) {
            builder.setMessage("暂无在用资产，请先新增资产。");
        } else {
            builder.setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    showAssetActionsDialog(activeAssets.get(which));
                }
            });
        }
        builder.setPositiveButton("新增资产", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showAssetEditDialog(null);
            }
        });
        builder.setNeutralButton("历史记录", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showAssetHistoryDialog();
            }
        });
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private void showAssetActionsDialog(final AssetLabel asset) {
        new AlertDialog.Builder(MainActivity.this).setTitle(asset.code).setItems(new String[]{"打印标签", "编辑资产", "归还/封存资产"}, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showPrintAssetCopiesDialog(asset);
                } else if (which == 1) {
                    showAssetEditDialog(asset);
                } else {
                    confirmDeleteAsset(asset);
                }
            }
        }).setNegativeButton("返回", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showAssetManagerDialog();
            }
        }).show();
    }

    private void confirmDeleteAsset(final AssetLabel asset) {
        new AlertDialog.Builder(MainActivity.this).setTitle("归还/封存资产").setMessage("确定将 " + asset.code + " 归还并移入历史记录吗？").setPositiveButton("归还并封存", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                archiveAsset(asset, "手动归还");
                Toast.makeText(MainActivity.this, "资产已移入历史记录", Toast.LENGTH_SHORT).show();
                showAssetManagerDialog();
            }
        }).setNegativeButton("取消", null).show();
    }

    private void showAssetHistoryDialog() {
        final List<AssetLabel> archivedAssets = getArchivedAssets();
        String[] items = new String[archivedAssets.size()];
        for (int i = 0; i < archivedAssets.size(); i++) {
            items[i] = archivedAssets.get(i).historyDisplayText();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("历史记录");
        if (items.length == 0) {
            builder.setMessage("暂无历史记录。");
        } else {
            builder.setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    showArchivedAssetActionsDialog(archivedAssets.get(which));
                }
            });
        }
        builder.setPositiveButton("返回资产表", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showAssetManagerDialog();
            }
        });
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private void showArchivedAssetActionsDialog(final AssetLabel asset) {
        new AlertDialog.Builder(MainActivity.this).setTitle(asset.code).setItems(new String[]{"查看详情", "恢复资产"}, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showAssetDetailDialog(asset);
                } else if (restoreAsset(asset)) {
                    Toast.makeText(MainActivity.this, "资产已恢复", Toast.LENGTH_SHORT).show();
                    showAssetManagerDialog();
                }
            }
        }).setNegativeButton("返回", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showAssetHistoryDialog();
            }
        }).show();
    }

    private void showAssetDetailDialog(final AssetLabel asset) {
        String message = "资产编码: " + safeText(asset.code)
                + "\nSN: " + safeText(asset.sn)
                + "\n资产名称: " + safeText(asset.name)
                + "\n使用人: " + safeText(asset.user)
                + "\n购买日期: " + safeText(asset.buyDate)
                + "\n状态: " + (asset.archived ? "已归还" : "在用")
                + "\n创建时间: " + formatTime(asset.createdAt)
                + "\n更新时间: " + formatTime(asset.updatedAt)
                + "\n归还时间: " + formatTime(asset.archivedAt)
                + "\n归还原因: " + safeText(asset.archiveReason)
                + "\n备注: " + safeText(asset.remark);
        new AlertDialog.Builder(MainActivity.this).setTitle("资产详情").setMessage(message).setPositiveButton("确定", null).show();
    }

    private void showAssetEditDialog(final AssetLabel editingAsset) {
        final AssetLabel asset = editingAsset == null ? new AssetLabel() : editingAsset;
        final boolean newAsset = editingAsset == null || !assetLabels.contains(editingAsset);
        if (newAsset) {
            long now = System.currentTimeMillis();
            if (TextUtils.isEmpty(asset.code)) {
                asset.code = generateAssetCode();
            }
            if (asset.createdAt == 0) {
                asset.createdAt = now;
            }
            asset.updatedAt = now;
        }
        LinearLayout form = new LinearLayout(MainActivity.this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(20, 20, 20, 20);
        TextView codeText = new TextView(MainActivity.this);
        codeText.setText("资产编码：" + safeText(asset.code));
        form.addView(codeText);
        final EditText snEdit = addEditText(form, "SN：", safeText(asset.sn));
        Button scanButton = new Button(MainActivity.this);
        scanButton.setText("扫码录入资产 SN");
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pendingScanSnEditText = snEdit;
                pendingScanEditingAsset = editingAsset;
                scanSnWithExternalApp();
            }
        });
        form.addView(scanButton);
        final EditText nameEdit = addEditText(form, "资产名称：", safeText(asset.name));
        final EditText userEdit = addEditText(form, "使用人：", safeText(asset.user));
        final EditText buyDateEdit = addEditText(form, "购买日期：", safeText(asset.buyDate));
        buyDateEdit.setHint("例如：2026年06月17日");
        final EditText remarkEdit = addEditText(form, "备注：", safeText(asset.remark));
        ScrollView scrollView = new ScrollView(MainActivity.this);
        scrollView.addView(form);
        new AlertDialog.Builder(MainActivity.this).setTitle(editingAsset == null ? "新增资产" : "编辑资产").setView(scrollView).setPositiveButton("保存", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                asset.sn = snEdit.getText().toString().trim();
                asset.name = nameEdit.getText().toString().trim();
                asset.user = userEdit.getText().toString().trim();
                asset.buyDate = buyDateEdit.getText().toString().trim();
                asset.remark = remarkEdit.getText().toString().trim();
                asset.updatedAt = System.currentTimeMillis();
                AssetStore.addEvent(asset, newAsset ? "新增" : "编辑", "资产资料保存");
                if (newAsset) {
                    assetLabels.add(asset);
                }
                saveAssetLabels();
                showAssetManagerDialog();
            }
        }).setNegativeButton("取消", null).show();
    }

    private EditText addEditText(LinearLayout form, String title, String value) {
        TextView titleView = new TextView(MainActivity.this);
        titleView.setText(title);
        form.addView(titleView);
        EditText editText = new EditText(MainActivity.this);
        editText.setSingleLine(true);
        editText.setText(value);
        form.addView(editText);
        return editText;
    }

    private void scanAssetQuickly() {
        pendingScanSnEditText = null;
        pendingScanEditingAsset = null;
        scanSnWithExternalApp();
    }

    private void scanSnWithExternalApp() {
        startActivityForResult(new Intent(MainActivity.this, AssetMlkitScanActivity.class), REQUEST_SCAN_SN);
    }

    private void handleScannedSn(final String sn) {
        final AssetLabel existingAsset = findActiveAssetBySn(sn, pendingScanEditingAsset);
        if (existingAsset != null) {
            Toast.makeText(MainActivity.this, "该SN已存在：" + existingAsset.code, Toast.LENGTH_LONG).show();
            new AlertDialog.Builder(MainActivity.this).setTitle("资产已存在")
                    .setMessage("SN " + sn + " 已属于资产 " + existingAsset.code + "。是否归还资产并移入历史？")
                    .setPositiveButton("归还并封存", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            archiveAsset(existingAsset, "扫码归还");
                            Toast.makeText(MainActivity.this, "资产已归还并移入历史", Toast.LENGTH_SHORT).show();
                            if (pendingScanSnEditText != null) {
                                pendingScanSnEditText.setText("");
                            }
                            pendingScanSnEditText = null;
                            showAssetManagerDialog();
                        }
                    })
                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            pendingScanSnEditText = null;
                        }
                    })
                    .show();
            return;
        }
        if (pendingScanSnEditText != null) {
            pendingScanSnEditText.setText(sn);
            pendingScanSnEditText.setSelection(sn.length());
        } else {
            showAssetEditDialogWithSn(sn);
        }
        pendingScanSnEditText = null;
    }

    private void showAssetEditDialogWithSn(String sn) {
        AssetLabel asset = new AssetLabel();
        long now = System.currentTimeMillis();
        asset.code = generateAssetCode();
        asset.createdAt = now;
        asset.updatedAt = now;
        asset.sn = sn;
        showAssetEditDialog(asset);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCAN_SN) {
            String sn = normalizeScannedSn(data == null ? null : data.getStringExtra(AssetScanActivity.EXTRA_SCAN_RESULT));
            if (!TextUtils.isEmpty(sn)) {
                handleScannedSn(sn);
            }
            pendingScanEditingAsset = null;
        } else if (requestCode == REQUEST_EXPORT_ASSETS && resultCode == RESULT_OK && data != null && data.getData() != null) {
            writeAssetsCsv(data.getData());
        } else if (requestCode == REQUEST_ASSET_MANAGER && resultCode == RESULT_OK && data != null) {
            loadAssetLabels();
            AssetLabel asset = findAssetByCode(data.getStringExtra(AssetManagerActivity.EXTRA_PRINT_ASSET_CODE));
            if (asset != null) {
                showPrintAssetCopiesDialog(asset);
            }
        }
    }

    private AssetLabel findAssetByCode(String code) {
        String value = safeText(code);
        for (AssetLabel asset : assetLabels) {
            if (value.equals(asset.code)) {
                return asset;
            }
        }
        return null;
    }

    private void showPreviewBitmapDialog(final Bitmap bitmap) {
        ImageView imageView = new ImageView(MainActivity.this);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }
        new AlertDialog.Builder(MainActivity.this).setTitle("打印预览").setView(imageView).setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                api.printBitmap(bitmap, null);
            }
        }).setNegativeButton("取消", null).show();
    }

    private void refreshPrintParamView() {
        btnConnectDevice.setText("");
        btnGapType.setText("间隔类型：\n" + gapTypeList[gapType + 1]);
        btnGapLength.setText("间隔长度：\n" + gapLength);
        btnPrintDensity.setText("打印浓度：\n" + printDensityList[printDensity + 1]);
        btnPrintSpeed.setText("打印速度：\n" + printSpeedList[printSpeed + 1]);
    }

    private void onPrinterConnecting(PrinterAddress printer, boolean showDialog) {
        String text = printer.shownName;
        if (TextUtils.isEmpty(text)) {
            text = printer.macAddress;
        }
        text = String.format("正在连接[%s]打印机", text);
        if (showDialog) {
            showStateAlertDialog(text);
        }
        btnConnectDevice.setText(text);
    }

    private void onPrinterConnected(PrinterAddress printer) {
        clearAlertDialog();
        saveLastPrinter(printer);
        Toast.makeText(MainActivity.this, "连接打印机成功！", Toast.LENGTH_SHORT).show();
        String text = "打印机：" + api.getPrinterInfo().deviceName + "\n" + api.getPrinterInfo().deviceAddress;
        btnConnectDevice.setText(text);
    }

    private void onPrinterDisconnected() {
        clearAlertDialog();
        Toast.makeText(MainActivity.this, "连接打印机失败！", Toast.LENGTH_SHORT).show();
        gapType = -1;
        gapLength = 3;
        printDensity = -1;
        printSpeed = -1;
        refreshPrintParamView();
    }

    private void onPrintStart() {
        showStateAlertDialog("正在打印标签...");
    }

    private void onPrintSuccess() {
        clearAlertDialog();
        Toast.makeText(MainActivity.this, "标签打印成功！", Toast.LENGTH_SHORT).show();
    }

    private void onPrintFailed() {
        clearAlertDialog();
        Toast.makeText(MainActivity.this, "标签打印失败！", Toast.LENGTH_SHORT).show();
    }

    private void showStateAlertDialog(String text) {
        if (stateAlertDialog != null && stateAlertDialog.isShowing()) {
            stateAlertDialog.setTitle(text);
        } else {
            stateAlertDialog = new AlertDialog.Builder(MainActivity.this).setCancelable(false).setTitle(text).show();
        }
    }

    private void clearAlertDialog() {
        if (stateAlertDialog != null && stateAlertDialog.isShowing()) {
            stateAlertDialog.dismiss();
        }
        stateAlertDialog = null;
    }

    private void onPrinterDiscovered(final PrinterAddress address) {
        if (address == null) {
            return;
        }
        if (autoConnectingPrinter && isPreferredPrinter(address)) {
            autoConnectingPrinter = false;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    api.stopDiscovery();
                    if (api.openPrinterByAddress(address)) {
                        onPrinterConnecting(address, false);
                    }
                }
            });
            return;
        }
        if (discoveredPrinterList == null) {
            discoveredPrinterList = new ArrayList<>();
        }
        for (PrinterAddress savedAddress : new ArrayList<>(discoveredPrinterList)) {
            if (savedAddress != null && TextUtils.equals(savedAddress.shownName, address.shownName)) {
                return;
            }
        }
        discoveredPrinterList.add(address);
        new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (printerAdapter != null) {
                    printerAdapter.setData(discoveredPrinterList);
                    printerAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private boolean isPreferredPrinter(PrinterAddress address) {
        String name = safeText(address.shownName).toUpperCase(Locale.US).replace("-", "").replace("_", "").replace(" ", "");
        String mac = safeText(address.macAddress).toUpperCase(Locale.US);
        return name.contains(PREFERRED_PRINTER_NAME) || mac.contains(PREFERRED_PRINTER_NAME);
    }

    private class DeviceListItemClicker implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            api.stopDiscovery();
            PrinterAddress printer = discoveredPrinterList.get(which);
            if (printer != null) {
                if (api.getPrinterState().group() == 2 && TextUtils.equals(api.getPrinterName(), printer.shownName)) {
                    Toast.makeText(MainActivity.this, "当前打印机已连接", Toast.LENGTH_LONG).show();
                    return;
                }
                if (api.openPrinterByAddress(printer)) {
                    onPrinterConnecting(printer, true);
                    return;
                }
            }
            onPrinterDisconnected();
        }
    }

    private class PrintDensityItemClicker implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            printDensity = which - 1;
            btnPrintDensity.setText("打印浓度：\n" + printDensityList[which]);
            api.setPrintDarkness(printDensity);
        }
    }

    private class PrintSpeedItemClicker implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            printSpeed = which - 1;
            btnPrintSpeed.setText("打印速度：\n" + printSpeedList[which]);
            api.setPrintSpeed(printSpeed);
        }
    }

    private class GapTypeItemClicker implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            gapType = which - 1;
            btnGapType.setText("间隔类型：\n" + gapTypeList[which]);
            api.setPrintPageGapType(gapType);
        }
    }

    private class BitmapListItemClicker implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (isPrinterConnected()) {
                int orientation = bitmapOrientations.length > which ? bitmapOrientations[which] : 0;
                Bitmap bmp = printBitmaps.get(which);
                if (bmp != null && printBitmap(bmp, getPrintParam(1, orientation))) {
                    onPrintStart();
                    return;
                }
                onPrintFailed();
            }
        }
    }

    private void requestPermission() {
        String[] permissions = new String[]{
                Manifest.permission.BLUETOOTH,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA
        };
        requestPermissions(permissions, 0);
    }

    private View initView(String title1, String text1) {
        View view = View.inflate(MainActivity.this, R.layout.setvalue_item, null);
        ((TextView) view.findViewById(R.id.tv_title1)).setText(title1);
        et1 = view.findViewById(R.id.et_value1);
        et1.setText(text1 == null ? "" : text1);
        et1.setSelection(et1.getText().toString().length());
        return view;
    }

    private View init1DBarcodeParamView(String text1, String text2) {
        View view = View.inflate(MainActivity.this, R.layout.setvalue_item, null);
        ((LinearLayout) view.findViewById(R.id.ll_2)).setVisibility(View.VISIBLE);
        ((TextView) view.findViewById(R.id.tv_title1)).setText("文本数据：");
        et1 = view.findViewById(R.id.et_value1);
        et1.setText(text1 == null ? "" : text1);
        et1.setSelection(et1.getText().length());
        ((TextView) view.findViewById(R.id.tv_title2)).setText("一维码数据：");
        et2 = view.findViewById(R.id.et_value2);
        et2.setText(text2 == null ? "" : text2);
        et2.setSelection(et2.getText().toString().length());
        return view;
    }
}
