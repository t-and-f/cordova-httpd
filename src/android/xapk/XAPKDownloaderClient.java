package xapk;

import com.google.android.vending.expansion.downloader.DownloadProgressInfo;
import com.google.android.vending.expansion.downloader.Helpers;
import com.google.android.vending.expansion.downloader.impl.BroadcastDownloaderClient;

import 	android.app.AlertDialog;
import android.util.Log;

public class XAPKDownloaderClient extends BroadcastDownloaderClient {
    private static final String LOG_TAG = "XAPKDownloader";

    @Override 
    public void onDownloadStateChanged(int newState) {
        if (newState == STATE_COMPLETED) {
            // downloaded successfully...
        } else if (newState >= 15) {
            // failed
            int message = Helpers.getDownloaderStringResourceIDFromState(newState);
            Log.e(LOG_TAG, "Download failed: " + message);
        } 
    }
    
    @Override 
    public void onDownloadProgress(DownloadProgressInfo progress) {
        if (progress.mOverallTotal > 0) {
            // receive the download progress
            // you can then display the progress in your activity
            String progressAsText = Helpers.getDownloadProgressPercent(progress.mOverallProgress, 
                progress.mOverallTotal);
            Log.i(LOG_TAG, "downloading progress: " + progressAsText);
        }
    }
}