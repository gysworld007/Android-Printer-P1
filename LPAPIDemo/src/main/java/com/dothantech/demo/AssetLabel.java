package com.dothantech.demo;

public class AssetLabel {
    public String code;
    public String sn;
    public String name;
    public String user;
    public String buyDate;
    public String remark;
    public String eventsJson;
    public long createdAt;
    public long updatedAt;
    public boolean archived;
    public long archivedAt;
    public String archiveReason;

    public String displayText() {
        return AssetStore.safeText(code) + " / " + AssetStore.safeText(name) + "\nSN: " + AssetStore.safeText(sn);
    }

    public String historyDisplayText() {
        return displayText() + "\n归还时间: " + AssetStore.formatTime(archivedAt) + " " + AssetStore.safeText(archiveReason);
    }
}
