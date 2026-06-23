package com.dothantech.demo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AssetManagerActivity extends Activity {
    public static final String EXTRA_PRINT_ASSET_CODE = "asset_print_code";

    private static final int REQUEST_SCAN_SEARCH = 2001;
    private static final int REQUEST_BACKUP_JSON = 2002;
    private static final int REQUEST_RESTORE_JSON = 2003;
    private static final int REQUEST_EXPORT_CSV = 2004;

    private final List<AssetLabel> assetLabels = new ArrayList<>();
    private LinearLayout timelineLayout;
    private EditText searchEdit;
    private String currentKeyword = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadAssets();
        buildContentView();
        refreshTimeline();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAssets();
        refreshTimeline();
    }

    private void loadAssets() {
        assetLabels.clear();
        assetLabels.addAll(AssetStore.load(AssetManagerActivity.this));
    }

    private void saveAssets() {
        AssetStore.save(AssetManagerActivity.this, assetLabels);
    }

    private void buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(244, 248, 251));

        TextView title = new TextView(this);
        title.setText("资产台账管理");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(24, 18, 24, 18);
        title.setBackgroundColor(Color.rgb(15, 75, 95));
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout searchCard = card();
        searchEdit = new EditText(this);
        searchEdit.setSingleLine(true);
        searchEdit.setTextColor(Color.BLACK);
        searchEdit.setHintTextColor(Color.GRAY);
        searchEdit.setTextSize(18);
        searchEdit.setHint("输入 SN / 资产编码 / 名称 / 使用人");
        searchCard.addView(searchEdit, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout searchButtons = row();
        searchButtons.addView(button("搜索", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentKeyword = searchEdit.getText().toString();
                refreshTimeline();
            }
        }), weightParams());
        searchButtons.addView(button("扫码", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(AssetManagerActivity.this, AssetMlkitScanActivity.class), REQUEST_SCAN_SEARCH);
            }
        }), weightParams());
        searchButtons.addView(button("清空", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentKeyword = "";
                searchEdit.setText("");
                refreshTimeline();
            }
        }), weightParams());
        searchCard.addView(searchButtons);
        root.addView(searchCard);

        ScrollView scrollView = new ScrollView(this);
        timelineLayout = new LinearLayout(this);
        timelineLayout.setOrientation(LinearLayout.VERTICAL);
        timelineLayout.setPadding(16, 10, 16, 10);
        scrollView.addView(timelineLayout);
        root.addView(scrollView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bottom = card();
        LinearLayout firstRow = row();
        firstRow.addView(button("新增资产", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAssetEditDialog(null);
            }
        }), weightParams());
        firstRow.addView(button("导出CSV", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportCsv();
            }
        }), weightParams());
        bottom.addView(firstRow);

        LinearLayout secondRow = row();
        secondRow.addView(button("备份JSON", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                backupJson();
            }
        }), weightParams());
        secondRow.addView(button("从备份恢复", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restoreJson();
            }
        }), weightParams());
        bottom.addView(secondRow);
        root.addView(bottom);

        setContentView(root);
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(18, 14, 18, 14);
        layout.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(12, 12, 12, 0);
        layout.setLayoutParams(params);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        return layout;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(4, 8, 4, 0);
        return params;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(listener);
        return button;
    }

    private void refreshTimeline() {
        if (timelineLayout == null) {
            return;
        }
        timelineLayout.removeAllViews();
        List<AssetLabel> shownAssets = AssetStore.search(assetLabels, currentKeyword);
        if (shownAssets.size() == 0) {
            TextView empty = new TextView(this);
            empty.setText("没有找到资产记录");
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(20, 60, 20, 60);
            timelineLayout.addView(empty);
            return;
        }
        for (AssetLabel asset : shownAssets) {
            timelineLayout.addView(timelineItem(asset));
        }
    }

    private View timelineItem(final AssetLabel asset) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(18));

        TextView dot = new TextView(this);
        dot.setText(asset.archived ? "●" : "●");
        dot.setTextColor(asset.archived ? Color.GRAY : Color.rgb(23, 107, 135));
        dot.setTextSize(28);
        dot.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        row.addView(dot, new LinearLayout.LayoutParams(dp(32), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(this);
        body.setText(assetLine(asset));
        body.setTextSize(14);
        body.setTextColor(Color.rgb(38, 50, 56));
        body.setLineSpacing(dp(4), 1.0f);
        body.setPadding(dp(14), dp(14), dp(14), dp(18));
        body.setMinHeight(dp(asset.archived ? 150 : 126));
        body.setBackgroundColor(Color.WHITE);
        row.addView(body, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAssetActions(asset);
            }
        });
        return row;
    }

    private String assetLine(AssetLabel asset) {
        String status = asset.archived ? "已归还" : "在用";
        String text = status + "  " + AssetStore.safeText(asset.code)
                + "\nSN: " + AssetStore.safeText(asset.sn)
                + "\n名称: " + AssetStore.safeText(asset.name)
                + "\n使用人: " + AssetStore.safeText(asset.user) + "    更新: " + AssetStore.formatTime(asset.updatedAt);
        if (asset.archived) {
            text += "\n归还: " + AssetStore.formatTime(asset.archivedAt) + "  " + AssetStore.safeText(asset.archiveReason);
        }
        return text;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showAssetActions(final AssetLabel asset) {
        String[] actions = asset.archived
                ? new String[]{"查看详情", "打印标签", "恢复资产", "删除记录"}
                : new String[]{"查看详情", "打印标签", "编辑资产", "归还/封存", "删除记录"};
        new AlertDialog.Builder(this).setTitle(asset.code).setItems(actions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (asset.archived) {
                    if (which == 0) showAssetDetail(asset); else if (which == 1) requestPrintAsset(asset); else if (which == 2) restoreAsset(asset); else confirmDeleteAsset(asset);
                } else {
                    if (which == 0) showAssetDetail(asset); else if (which == 1) requestPrintAsset(asset); else if (which == 2) showAssetEditDialog(asset); else if (which == 3) archiveAsset(asset, "手动归还"); else confirmDeleteAsset(asset);
                }
            }
        }).setNegativeButton("取消", null).show();
    }

    private void requestPrintAsset(AssetLabel asset) {
        Intent data = new Intent();
        data.putExtra(EXTRA_PRINT_ASSET_CODE, AssetStore.safeText(asset.code));
        setResult(RESULT_OK, data);
        finish();
    }

    private void confirmDeleteAsset(final AssetLabel asset) {
        new AlertDialog.Builder(this)
                .setTitle("删除资产记录")
                .setMessage("确定永久删除 " + AssetStore.safeText(asset.code) + " 吗？删除后只能通过之前的JSON备份恢复。")
                .setPositiveButton("永久删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        assetLabels.remove(asset);
                        saveAssets();
                        refreshTimeline();
                        Toast.makeText(AssetManagerActivity.this, "资产记录已删除", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAssetDetail(AssetLabel asset) {
        String message = assetLine(asset) + "\n备注: " + AssetStore.safeText(asset.remark) + timelineEvents(asset);
        new AlertDialog.Builder(this).setTitle("资产详情").setMessage(message).setPositiveButton("确定", null).show();
    }

    private String timelineEvents(AssetLabel asset) {
        JSONArray events = AssetStore.eventsArray(asset);
        if (events.length() == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder("\n\n时间线:");
        for (int i = events.length() - 1; i >= 0; i--) {
            JSONObject event = events.optJSONObject(i);
            if (event != null) {
                builder.append('\n').append(AssetStore.formatTime(event.optLong("time"))).append(' ')
                        .append(event.optString("type", "")).append(' ')
                        .append(event.optString("note", ""));
            }
        }
        return builder.toString();
    }

    private void showAssetEditDialog(final AssetLabel editingAsset) {
        final AssetLabel asset = editingAsset == null ? new AssetLabel() : editingAsset;
        final boolean newAsset = editingAsset == null || !assetLabels.contains(editingAsset);
        long now = System.currentTimeMillis();
        if (newAsset) {
            asset.code = generateAssetCode();
            asset.createdAt = now;
            asset.updatedAt = now;
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(24, 16, 24, 16);
        final EditText snEdit = edit(form, "SN", asset.sn);
        final EditText nameEdit = edit(form, "资产名称", asset.name);
        final EditText userEdit = edit(form, "使用人", asset.user);
        final EditText buyDateEdit = edit(form, "购买日期", asset.buyDate);
        final EditText remarkEdit = edit(form, "备注", asset.remark);

        new AlertDialog.Builder(this).setTitle(newAsset ? "新增资产" : "编辑资产 " + asset.code).setView(form).setPositiveButton("保存", new DialogInterface.OnClickListener() {
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
                saveAssets();
                refreshTimeline();
            }
        }).setNegativeButton("取消", null).show();
    }

    private EditText edit(LinearLayout form, String title, String value) {
        TextView textView = new TextView(this);
        textView.setText(title);
        form.addView(textView);
        EditText editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setTextColor(Color.BLACK);
        editText.setHintTextColor(Color.GRAY);
        editText.setText(AssetStore.safeText(value));
        form.addView(editText);
        return editText;
    }

    private void archiveAsset(AssetLabel asset, String reason) {
        asset.archived = true;
        asset.archivedAt = System.currentTimeMillis();
        asset.archiveReason = reason;
        asset.updatedAt = asset.archivedAt;
        AssetStore.addEvent(asset, "归还", reason);
        saveAssets();
        refreshTimeline();
        Toast.makeText(this, "资产已移入历史", Toast.LENGTH_SHORT).show();
    }

    private void restoreAsset(AssetLabel asset) {
        if (AssetStore.findActiveBySn(assetLabels, asset.sn, asset) != null) {
            Toast.makeText(this, "在用资产中已存在相同SN，无法恢复", Toast.LENGTH_LONG).show();
            return;
        }
        asset.archived = false;
        asset.archivedAt = 0;
        asset.archiveReason = "";
        asset.updatedAt = System.currentTimeMillis();
        AssetStore.addEvent(asset, "恢复", "从历史记录恢复");
        saveAssets();
        refreshTimeline();
        Toast.makeText(this, "资产已恢复", Toast.LENGTH_SHORT).show();
    }

    private String generateAssetCode() {
        String datePart = new SimpleDateFormat("yyMMdd", Locale.US).format(new Date());
        String prefix = "A" + datePart;
        int maxIndex = 0;
        for (AssetLabel asset : assetLabels) {
            if (asset.code != null && asset.code.startsWith(prefix) && asset.code.length() >= prefix.length() + 3) {
                try {
                    int index = Integer.parseInt(asset.code.substring(prefix.length()));
                    if (index > maxIndex) maxIndex = index;
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }
        return prefix + String.format(Locale.US, "%03d", maxIndex + 1);
    }

    private void exportCsv() {
        String fileName = "资产台账_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".csv";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, REQUEST_EXPORT_CSV);
    }

    private void backupJson() {
        String fileName = "资产台账备份_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, REQUEST_BACKUP_JSON);
    }

    private void restoreJson() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_RESTORE_JSON);
    }

    private void writeBackup(Uri uri) {
        writeText(uri, AssetStore.toBackupJson(assetLabels), "备份完成");
    }

    private void writeCsv(Uri uri) {
        StringBuilder builder = new StringBuilder();
        builder.append("资产编码,SN,资产名称,使用人,购买日期,状态,创建时间,更新时间,归还时间,归还原因,备注\n");
        for (AssetLabel asset : assetLabels) {
            builder.append(csvCell(asset.code)).append(',')
                    .append(csvCell(asset.sn)).append(',')
                    .append(csvCell(asset.name)).append(',')
                    .append(csvCell(asset.user)).append(',')
                    .append(csvCell(asset.buyDate)).append(',')
                    .append(csvCell(asset.archived ? "已归还" : "在用")).append(',')
                    .append(csvCell(AssetStore.formatTime(asset.createdAt))).append(',')
                    .append(csvCell(AssetStore.formatTime(asset.updatedAt))).append(',')
                    .append(csvCell(AssetStore.formatTime(asset.archivedAt))).append(',')
                    .append(csvCell(asset.archiveReason)).append(',')
                    .append(csvCell(asset.remark)).append('\n');
        }
        writeText(uri, builder.toString(), "导出完成");
    }

    private void writeText(Uri uri, String text, String toast) {
        OutputStream outputStream = null;
        try {
            outputStream = getContentResolver().openOutputStream(uri);
            if (outputStream == null) return;
            outputStream.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            outputStream.write(text.getBytes("UTF-8"));
            Toast.makeText(this, toast, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "写入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (outputStream != null) {
                try { outputStream.close(); } catch (IOException e) { e.printStackTrace(); }
            }
        }
    }

    private void readRestore(final Uri uri) {
        try {
            final List<AssetLabel> restored = AssetStore.fromBackupJson(readText(uri));
            new AlertDialog.Builder(this).setTitle("恢复备份")
                    .setMessage("备份中包含 " + restored.size() + " 条资产记录。确定覆盖当前台账吗？")
                    .setPositiveButton("覆盖恢复", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            assetLabels.clear();
                            assetLabels.addAll(restored);
                            saveAssets();
                            refreshTimeline();
                            Toast.makeText(AssetManagerActivity.this, "恢复完成", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("取消", null).show();
        } catch (Exception e) {
            Toast.makeText(this, "恢复失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String readText(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        if (inputStream == null) return "";
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, count);
        }
        inputStream.close();
        return outputStream.toString("UTF-8");
    }

    private static String csvCell(String value) {
        String text = AssetStore.safeText(value);
        if (text.contains("\"") || text.contains(",") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        if (requestCode == REQUEST_SCAN_SEARCH) {
            String sn = AssetStore.normalizeScannedSn(data.getStringExtra(AssetScanActivity.EXTRA_SCAN_RESULT));
            searchEdit.setText(sn);
            currentKeyword = sn;
            if (AssetStore.findActiveBySn(assetLabels, sn, null) == null) {
                Toast.makeText(this, "未找到在用资产，可新增登记", Toast.LENGTH_LONG).show();
            }
            refreshTimeline();
        } else if (data.getData() == null) {
            return;
        } else if (requestCode == REQUEST_BACKUP_JSON) {
            writeBackup(data.getData());
        } else if (requestCode == REQUEST_RESTORE_JSON) {
            readRestore(data.getData());
        } else if (requestCode == REQUEST_EXPORT_CSV) {
            writeCsv(data.getData());
        }
    }
}
