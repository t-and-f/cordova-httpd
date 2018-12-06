package com.rjfun.cordova.httpd;

import xapk.XAPKDownloaderActivity;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;

import android.content.Context;
import android.content.Intent;

public class DownloadActivityStub implements Runnable {

    private CordovaInterface cordova = null;

    private CordovaWebView webView = null;

    public DownloadActivityStub(CordovaInterface cordova, CordovaWebView webView) {
        this.cordova = cordova;
        this.webView = webView;
    }

    @Override
    public void run() {
        XAPKDownloaderActivity.cordovaActivity = this.cordova.getActivity(); // Workaround for Cordova/Crosswalk
                                                                        // flickering status bar bug.
        // Provide a webview to Downloader Activity so it can trigger a page reload once
        // the expansion is downloaded.
        XAPKDownloaderActivity.cordovaWebView = this.webView;
        Context context = this.cordova.getActivity().getApplicationContext();
        Intent intent = new Intent(context, XAPKDownloaderActivity.class);
        this.cordova.getActivity().startActivity(intent);
    }
}