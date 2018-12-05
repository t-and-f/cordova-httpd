package xapk;

import com.rjfun.cordova.httpd.CordovaUtils;

import android.content.Context;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Messenger;
import android.support.v4.content.LocalBroadcastManager;

// <Workaround for Cordova/Crosswalk flickering status bar bug./>
import android.view.WindowManager;
// <Workaround for Cordova/Crosswalk flickering status bar bug./>
import android.util.Log;

import com.google.android.vending.expansion.downloader.DownloadProgressInfo;
import com.google.android.vending.expansion.downloader.Helpers;
import com.google.android.vending.expansion.downloader.impl.DownloaderProxy;

import org.apache.cordova.CordovaWebView;
import java.io.File;

public class XAPKDownloaderActivity extends Activity {
	private final XAPKDownloaderClient mClient = new XAPKDownloaderClient();
	private final DownloaderProxy mDownloaderProxy = new DownloaderProxy(this);
	private ProgressDialog mProgressDialog;
	private static final String LOG_TAG = "XAPKDownloader";
	private Bundle xmlData;
	private int[] versionList = new int[2];
	private long[] fileSizeList = new long[2];
	private boolean progressInMB = false;
	private boolean autoReload = true;
	private Bundle bundle;

	// <Workaround for Cordova/Crosswalk flickering status bar bug./>
	public static Activity cordovaActivity = null;
	// <Workaround for Cordova/Crosswalk flickering status bar bug./>
	// The Cordova webview, so we can tell it to reload the page once the contents
	// have been received.
	public static CordovaWebView cordovaWebView = null;

	// The file may have been delivered by Google Play --- let's make sure it exists
	// and it's the size we expect.
	static public boolean validateFile(Context ctx, String fileName, long fileSize, boolean checkFileSize) {
		File fileForNewFile = new File(Helpers.generateSaveFileName(ctx, fileName));
		if (!fileForNewFile.exists())
			return false;
		if ((checkFileSize == true) && (fileForNewFile.length() != fileSize))
			return false;
		return true;
	}

	// Determine whether we know if all the expansion files were delivered.
	boolean allExpansionFilesKnownAsDelivered(int[] versionList, long[] fileSizeList) {
		for (int i = 0; i < 2; i++) {
			// A -1 indicates we're not using this file (patch/main).
			if (versionList[i] == -1)
				continue;
			// If the version number is 0, we don't know if all expansion files were
			// delivered, so return value should .
			if (versionList[i] == 0)
				return false;
			String fileName = Helpers.getExpansionAPKFileName(this, (i == 0), versionList[i]);
			// If the file doesn't exist or has the wrong file size, consider the files to
			// be undelivered.
			if (!validateFile(this, fileName, fileSizeList[i], (fileSizeList[i] != 0))) {
				Log.e(LOG_TAG, "ExpansionAPKFile doesn't exist or has a wrong size (" + fileName + ").");
				return false;
			}
		}
		return true;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		CordovaUtils utils = new CordovaUtils();

		if (cordovaActivity != null) {
			// <Workaround for Cordova/Crosswalk flickering status bar bug./>
			cordovaActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
			cordovaActivity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
			// <Workaround for Cordova/Crosswalk flickering status bar bug./>
		}

		super.onCreate(savedInstanceState);
		mDownloaderProxy.connect();
		xmlData = getIntent().getExtras(); // savedInstanceState;
		versionList[0] = this.getIntent().getIntExtra("xapk_main_version", 0);
		versionList[1] = this.getIntent().getIntExtra("xapk_patch_version", 0);
		fileSizeList[0] = this.getIntent().getLongExtra("xapk_main_file_size", 0L);
		fileSizeList[1] = this.getIntent().getLongExtra("xapk_patch_file_size", 0L);
		String progressFormat = this.getIntent().getStringExtra("xapk_progress_format");
		Boolean autoReload = this.getIntent().getBooleanExtra("xapk_auto_reload", true);
		if (progressFormat != null && progressFormat.toLowerCase().equals("megabytes")) {
			this.progressInMB = true;
		}

		if (autoReload != null) {
			this.autoReload = autoReload;
		}

		// Check if both expansion files are already available and downloaded before
		// going any further.
		if (allExpansionFilesKnownAsDelivered(versionList, fileSizeList)) {
			Log.v(LOG_TAG, "Files are already present.");
			finish();
			return;
		}

		// Download the expansion file(s).
		try {
			Intent launchIntent = this.getIntent();

			// Build an Intent to start this activity from the Notification.
			Intent notifierIntent = new Intent(XAPKDownloaderActivity.this, XAPKDownloaderActivity.this.getClass());
			notifierIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			notifierIntent.setAction(launchIntent.getAction());
			// notifierIntent.putExtras(launchIntent.getExtras());

			if (launchIntent.getCategories() != null) {
				for (String category : launchIntent.getCategories()) {
					notifierIntent.addCategory(category);
				}
			}

			PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifierIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);

			// We can't attempt downloading the files with a debug signature.
			if (utils.signatureIsDebug(this)) {
				Log.v(LOG_TAG, "Using debug signature: no download is possible.");
				finish();
				return;
			}

			// Start the download service, if required.
			Log.v(LOG_TAG, "Starting the download service.");
			XAPKDownloaderService service = new XAPKDownloaderService();
			int startResult = XAPKDownloaderService.startDownloadServiceIfRequired(this, "", pendingIntent,
					service.getSALT(), service.getPublicKey());

			if (startResult == XAPKDownloaderService.NO_DOWNLOAD_REQUIRED) {
				if (!autoReload) {
					final Intent intent = new Intent("XAPK_Download_finished");
					LocalBroadcastManager.getInstance(this).sendBroadcastSync(intent);
				}
				Log.v(LOG_TAG, "No download required.");
				finish();
				return;
			}

			// If download has started, initialize activity to show progress.
			Log.v(LOG_TAG, "Initializing activity to show progress.");
			// Shows download progress.
			mProgressDialog = new ProgressDialog(XAPKDownloaderActivity.this);
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			// mProgressDialog.setMessage(xmlData.getString("xapk_text_downloading_assets", ""));
			mProgressDialog.setCancelable(false);

			// Setup for displaying progress in MB (instead of default, percentage)
			if (progressInMB) {
				// Initial guess at file size to download. This will probably be incorrect
				// unless you're
				// using XAPK_MAIN_SIZE and XAPK_PATCH_SIZE, but we'll update it when we get the
				// first
				// progress callback from the downloader.
				int totalMB = 0;
				for (int i = 0; i < fileSizeList.length; i++) {
					if (fileSizeList[i] > 0) {
						totalMB += fileSizeList[i];
					}
				}
				mProgressDialog.setMax((int) (fileSizeList[0] / 1024 / 1024));
				mProgressDialog.setProgressNumberFormat("%1dMB / %2dMB");
			}

			mProgressDialog.show();
			return;

		} catch (NameNotFoundException e) {
			Log.e(LOG_TAG, "Cannot find own package! MAYDAY!");
			e.printStackTrace();
		} catch (Exception e) {
			Log.e(LOG_TAG, "Exception during startup", e);
			e.printStackTrace();
		}

		// Finish the activity.
		finish();
	}

	// Connect the stub to our service on start.
	@Override
	protected void onStart() {
		super.onStart();
		this.mClient.register(this);
	}

	// Connect the stub from our service on resume.
	@Override
	protected void onResume() {
		super.onResume();
	}

	// Disconnect the stub from our service on stop.
	@Override
	protected void onStop() {
		this.mClient.unregister(this);
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		// Don't forget to unbind the proxy when you don't
		// need it anymore
		mDownloaderProxy.disconnect();
	}
}
