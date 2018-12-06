package xapk;

import com.google.android.vending.expansion.downloader.DownloadProgressInfo;
import com.google.android.vending.expansion.downloader.Helpers;
import com.google.android.vending.expansion.downloader.impl.BroadcastDownloaderClient;

import java.io.InputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;

import android.content.Context;
import android.content.Intent;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.support.v4.content.LocalBroadcastManager;

import android.util.Log;

public class XAPKDownloaderClient extends BroadcastDownloaderClient {
    private static final String LOG_TAG = "XAPKDownloader";

    private Activity downloaderActivity = null;

    private NotificationManager notificationManager = null;

    private int notificationID = 0;

    private Notification.Builder notificationBuilder = null;

    public XAPKDownloaderClient(Activity activity) {
        this.downloaderActivity = activity;
    }

    private int createID() {
        Date now = new Date();
        int id = Integer.parseInt(new SimpleDateFormat("ddHHmmss", Locale.US).format(now));
        return id;
    }

    public void init() {
        this.notificationID = this.createID();
        this.notificationManager = (NotificationManager) this.downloaderActivity.getSystemService(Context.NOTIFICATION_SERVICE);

        // Android 26+ notifications MUST implement channels.
        // https://developer.android.com/training/notify-user/channels
        //
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            String CHANNEL_ID = "kano_xapk_channel";
            CharSequence name = "kano_xapk";
            String Description = "Notification channel for XAPK download";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            android.app.NotificationChannel mChannel = new android.app.NotificationChannel(CHANNEL_ID, name, importance);
            mChannel.setDescription(Description);
            mChannel.enableLights(false);
            // mChannel.setLightColor(Color.RED);
            mChannel.enableVibration(false);
            // mChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
            mChannel.setShowBadge(false);
            notificationManager.createNotificationChannel(mChannel);
        }

        // Set notification information:
        this.notificationBuilder = new Notification.Builder(this.downloaderActivity);
        this.notificationBuilder
            .setOngoing(true)
            .setContentTitle("Downloading assets")
            // .setContentText("Notification Content Text")
            .setSmallIcon(this.downloaderActivity.getApplicationInfo().icon)
            .setProgress(100, 0, false);

        // Send the notification:
        Notification notification = notificationBuilder.build();
        this.notificationManager.notify(notificationID, notification);
    }

    @Override
    public void onDownloadStateChanged(int newState) {
        if (newState == STATE_COMPLETED) {
            // downloaded successfully
            final Intent intent = new Intent("XAPK_download_finished");
            LocalBroadcastManager.getInstance(this.downloaderActivity.getApplicationContext()).sendBroadcastSync(intent);
        } else if (newState >= 15) {
            // failed
            int message = Helpers.getDownloaderStringResourceIDFromState(newState);
            Log.e(LOG_TAG, "Download failed: " + message);
        }
    }

    @Override
    public void onDownloadProgress(DownloadProgressInfo progressInfo) {
        if (progressInfo.mOverallTotal > 0) {
            // Receive the download progress
            String progressAsText = Helpers.getDownloadProgressPercent(progressInfo.mOverallProgress,
                    progressInfo.mOverallTotal);
            long progress = progressInfo.mOverallProgress * 100 / progressInfo.mOverallTotal;
            
            // Log.i(LOG_TAG, "Download progress: " + progressAsText);

            // Update notification information:
            this.notificationBuilder.setProgress(100, (int)progress, false);

            // Send the notification:
            Notification notification = notificationBuilder.build();
            this.notificationManager.notify(this.notificationID, notification);

            // Broadcast progress (in-app) 
            Intent intent = new Intent("XAPK_download_progress");
            // Add some data
            intent.putExtra("progressPercent", progress);
            LocalBroadcastManager.getInstance(this.downloaderActivity.getApplicationContext()).sendBroadcastSync(intent);
        }
    }
}