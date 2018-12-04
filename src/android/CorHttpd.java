package com.rjfun.cordova.httpd;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Enumeration;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;

import android.util.Log;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.BroadcastReceiver;
import android.support.v4.content.LocalBroadcastManager;
import android.content.IntentFilter;

/**
 * This class echoes a string called from JavaScript.
 */
public class CorHttpd extends CordovaPlugin {

    /** Common tag used for logging statements. */
    private static final String LOGTAG = "CorHttpd";

    /** Cordova Actions. */
    private static final String ACTION_GET_URL = "getURL";
    private static final String ACTION_GET_LOCAL_PATH = "getLocalPath";
    private static final String ACTION_OBSERVE_DOWNLOAD_PROGRESS = "observeDownloadProgress";

    private static final String OPT_WWW_ROOT = "Root";
    private static final String OPT_PORT = "Port";
    private static final String OPT_LOCALHOST_ONLY = "LocalhostOnly";

    private String www_root = "";
    private int port = 8888;
    private boolean localhost_only = true;

    private String localPath = "";
    private WebServer server = null;
    private String url = "";

    private static CallbackContext downloadProgressObserver = null;

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Extract data included in the Intent
            long downloadProgress = intent.getLongExtra("progressPercent", -1L/* default value */);
            downloadProgressObserver.success((int)downloadProgress);
        }
    };

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        www_root = preferences.getString(OPT_WWW_ROOT, "");
        port = preferences.getInteger(OPT_PORT, 8888);
        localhost_only = preferences.getBoolean(OPT_LOCALHOST_ONLY, true);
    }

    @Override
    public void onStart() {
        if (www_root.startsWith("/")) {
            localPath = www_root;
        } else {
            localPath = "www";
            if (www_root.length() > 0) {
                localPath += "/";
                localPath += www_root;
            }
        }
        __startServer();
    }

    @Override
    public void onStop() {
        __stopServer();
    }

    @Override
    public boolean execute(String action, JSONArray inputs, CallbackContext callbackContext) throws JSONException {
        PluginResult result = null;
        if (ACTION_GET_URL.equals(action)) {
            result = getURL(inputs, callbackContext);

        } else if (ACTION_GET_LOCAL_PATH.equals(action)) {
            result = getLocalPath(inputs, callbackContext);

        } else if (ACTION_OBSERVE_DOWNLOAD_PROGRESS.equals(action)) {
            downloadProgressObserver = callbackContext;
            result = new PluginResult(Status.OK);
            result.setKeepCallback(true);
        }  else {
            Log.d(LOGTAG, String.format("Invalid action passed: %s", action));
            result = new PluginResult(Status.INVALID_ACTION);
        }

        if (result != null)
            callbackContext.sendPluginResult(result);

        return true;
    }

    private String __startServer() {
        String errmsg = "";
        try {
            AndroidFile f = new AndroidFile(localPath);

            Context ctx = cordova.getActivity().getApplicationContext();
            AssetManager am = ctx.getResources().getAssets();
            f.setAssetManager(am);

            if (localhost_only) {
                InetSocketAddress localAddr = new InetSocketAddress(
                        InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }), port);
                server = new WebServer(localAddr, f, cordova, webView);
            } else {
                server = new WebServer(port, f, cordova, webView);
            }
        } catch (IOException e) {
            errmsg = String.format("IO Exception: %s", e.getMessage());
            Log.w(LOGTAG, errmsg);
        }
        return errmsg;
    }

    private void __stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    private PluginResult getURL(JSONArray inputs, CallbackContext callbackContext) {
        Log.w(LOGTAG, "getURL");

        callbackContext.success(this.url);
        return null;
    }

    private PluginResult getLocalPath(JSONArray inputs, CallbackContext callbackContext) {
        Log.w(LOGTAG, "getLocalPath");

        callbackContext.success(this.localPath);
        return null;
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     *
     * @param multitasking Flag indicating if multitasking is turned on for app
     */
    public void onPause(boolean multitasking) {
        // if(! multitasking) __stopServer();
        LocalBroadcastManager.getInstance(this.cordova.getActivity().getApplicationContext())
            .unregisterReceiver(this.mMessageReceiver);
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     * @param multitasking Flag indicating if multitasking is turned on for app
     */
    public void onResume(boolean multitasking) {
        // if(! multitasking) __startServer();
        LocalBroadcastManager.getInstance(this.cordova.getActivity().getApplicationContext())
            .registerReceiver(this.mMessageReceiver,
                          new IntentFilter("XAPK_download_progress"));
    }

    /**
     * The final call you receive before your activity is destroyed.
     */
    public void onDestroy() {
        __stopServer();
        LocalBroadcastManager.getInstance(this.cordova.getActivity().getApplicationContext())
            .unregisterReceiver(this.mMessageReceiver);
        super.onDestroy();
    }
}
