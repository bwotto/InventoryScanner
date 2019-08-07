package com.sierracharter.inventoryscanner;

public interface DownloadCallback{

    void onStartedSheetUpdate(String result);

    void onFinishedSheetUpdate(String result);
}
