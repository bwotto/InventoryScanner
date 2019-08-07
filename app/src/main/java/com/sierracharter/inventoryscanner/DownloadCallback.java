package com.sierracharter.inventoryscanner;

public interface DownloadCallback{

    void onStartedSheetUpdate(Exception e);

    void onFinishedSheetUpdate(Exception e);
}
