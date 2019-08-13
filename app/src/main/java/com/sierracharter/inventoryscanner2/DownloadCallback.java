package com.sierracharter.inventoryscanner2;

public interface DownloadCallback{

    void onStartedSheetUpdate(String result);

    void onFinishedSheetUpdate(String result);
}
