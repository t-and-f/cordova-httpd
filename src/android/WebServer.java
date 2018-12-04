package com.rjfun.cordova.httpd;

import xapk.*;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

public class WebServer extends NanoHTTPD {
	private final String LOGTAG = "CorHTTPD";

	private final Boolean DEBUG = true;

	private CordovaInterface cordova = null;

	private CordovaWebView webView = null;

	private AndroidFile myRootDir = null;

	private XAPKZipResourceFile expansionFile = null;

	private String apkVersion = "1";

	private ArrayList<String> activePaths = new ArrayList<>();

	/**
	 * Hashtable mapping (String)FILENAME_EXTENSION -> (String)MIME_TYPE
	 */
	@SuppressWarnings("rawtypes")
	private Hashtable<String, String> mimeTypes = new Hashtable<>();
	{
		StringTokenizer st = new StringTokenizer("css		text/css " + "htm		text/html " + "html		text/html "
				+ "xml		text/xml " + "txt		text/plain " + "asc		text/plain " + "gif		image/gif "
				+ "jpg		image/jpeg " + "jpeg		image/jpeg " + "png		image/png " + "mp3		audio/mpeg "
				+ "m3u		audio/mpeg-url " + "mp4		video/mp4 " + "ogv		video/ogg " + "flv		video/x-flv "
				+ "mov		video/quicktime " + "swf		application/x-shockwave-flash "
				+ "js			application/javascript " + "pdf		application/pdf " + "doc		application/msword "
				+ "ogg		application/x-ogg " + "zip		application/octet-stream "
				+ "exe		application/octet-stream " + "svg		image/svg+xml "
				+ "class		application/octet-stream ");
		while (st.hasMoreTokens())
			mimeTypes.put(st.nextToken(), st.nextToken());
	}

	public WebServer(InetSocketAddress localAddr, AndroidFile wwwroot, CordovaInterface cordova, CordovaWebView webview)
			throws IOException {
		super(localAddr, wwwroot);
		this.init(wwwroot, cordova, webview);
	}

	public WebServer(int port, AndroidFile wwwroot, CordovaInterface cordova, CordovaWebView webview)
			throws IOException {
		super(port, wwwroot);
		this.init(wwwroot, cordova, webview);
	}

	private void parseAPKLayout() throws IOException, JsonParseException {
		// Read config of xapk(s).
		AssetManager assetManager = this.cordova.getActivity().getAssets();
		Gson gson = new Gson();
		BufferedReader configReader = new BufferedReader(
				new InputStreamReader(assetManager.open("www/www/xapk-conf.json")));

		JsonParser parser = new JsonParser();
		JsonElement rootNode = parser.parse(configReader);
		configReader.close();

		if (DEBUG)
			Log.i(LOGTAG, "Parsed JSON config to tree");
		if (rootNode.isJsonObject()) {
			JsonObject root = rootNode.getAsJsonObject();

			if (root.has("apkVersion")) {
				this.apkVersion = root.get("apkVersion").getAsString();
			}

			if (root.has("layout")) {
				JsonElement layout = root.get("layout");
				JsonObject layoutNode = layout.getAsJsonObject();

				if (layoutNode.has("main")) {
					JsonArray main = layoutNode.getAsJsonArray("main");
					String[] mainRecords = gson.fromJson(main, String[].class);
					for (String mainEntry : mainRecords) {
						if (DEBUG)
							Log.i(LOGTAG, mainEntry);
						this.activePaths.add(mainEntry);
					}
				}

				if (layoutNode.has("patch")) {
					JsonArray patch = layoutNode.getAsJsonArray("patch");
					String[] patchRecords = gson.fromJson(patch, String[].class);
					for (String patchEntry : patchRecords) {
						if (DEBUG)
							Log.i(LOGTAG, patchEntry);
						this.activePaths.add(patchEntry);
					}
				}
			}
		}
	}

	private void downloadExpansionIfAvailable() {
		cordova.getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				XAPKDownloaderActivity.cordovaActivity = cordova.getActivity(); // Workaround for Cordova/Crosswalk
																				// flickering status bar bug.
				// Provide a webview to Downloader Activity so it can trigger a page reload once
				// the expansion is downloaded.
				XAPKDownloaderActivity.cordovaWebView = webView;
				Context context = cordova.getActivity().getApplicationContext();
				Intent intent = new Intent(context, XAPKDownloaderActivity.class);
				cordova.getActivity().startActivity(intent);
			}
		});
	}

	private void init(AndroidFile wwwroot, CordovaInterface cordova, CordovaWebView webView) throws IOException {
		this.myRootDir = wwwroot;
		this.cordova = cordova;
		this.webView = webView;
		Context ctx = cordova.getActivity().getApplicationContext();

		try {
			this.parseAPKLayout();
		} catch (IOException | JsonParseException ex) {
			Log.e(LOGTAG, "Could not parse the apk layout file", ex);
		}

		// Retrieve the expansion file(s).
		if (DEBUG)
			Log.i(LOGTAG, "OBB dir: " + ctx.getObbDir());

		// By design, Google libraries bundle both APK Expansions, if available,
		// into one XAPKZipResourceFile resource. Any file is requested is
		// looked up first in the patch APK, then the main.
		int version = Integer.parseInt(this.apkVersion);

		if (utils.signatureIsDebug(ctx)) {
			this.expansionFile = XAPKExpansionSupport.getAPKExpansionZipFile(ctx, version, version);
			if (null != this.expansionFile) {
				if (DEBUG)
					Log.i(LOGTAG, "Expansion file: " + this.expansionFile.toString());
			}
		} else {
			CordovaUtils utils = new CordovaUtils();
			Map config = utils.loadConfigFromXml(cordova.getActivity().getResources(), ctx.getPackageName());

			if (DEBUG)
				Log.i(LOGTAG, config.toString());

			xapk.XAPKDownloaderService.BASE64_PUBLIC_KEY = config.get("XAPK_PUBLIC_KEY");
			this.downloadExpansionIfAvailable();
		}
	}

	/*
	 * (By default, this delegates to serveFile() and allows directory listing.)
	 *
	 * @param uri Percent-decoded URI without parameters, for example "/index.cgi"
	 * 
	 * @param method "GET", "POST" etc.
	 * 
	 * @param parms Parsed, percent decoded parameters from URI and, in case of
	 * POST, data.
	 * 
	 * @param header Header entries, percent decoded
	 * 
	 * @return HTTP response, see class Response for details
	 */
	@SuppressWarnings("rawtypes")
	public Response serve(String uri, String method, Properties header, Properties parms, Properties files) {
		boolean inApk = false;
		String realPath = uri.startsWith("/www/") ? uri.replaceFirst("/www/", "") : uri;

		// Check if uri is part of apk definitions
		for (String activePath : this.activePaths) {
			if (realPath.startsWith(activePath)) {
				inApk = true;
			}
		}

		if (inApk) {
			if (DEBUG)
				Log.i(LOGTAG, method + " '" + uri + "' is in target folder");
			return this.serveFromAPK(realPath, header, myRootDir);

		} else {
			if (DEBUG)
				Log.i(LOGTAG, method + " '" + uri + "' ");
			return serveFile(uri, header, myRootDir, true);
		}
	}

	/**
	 * Serves file from homeDir and its' subdirectories (only). Uses only URI,
	 * ignores all headers and HTTP parameters.
	 */
	private Response serveFromAPK(String path, Properties header, AndroidFile homeDir) {
		Response res = null;
		AssetFileDescriptor result = null;
		FileInputStream in = null;

		// Remove URL arguments
		path = path.trim().replace(File.separatorChar, '/');
		if (path.indexOf('?') >= 0)
			path = path.substring(0, path.indexOf('?'));

		// Prohibit getting out of current directory
		if (path.startsWith("..") || path.endsWith("..") || path.indexOf("../") >= 0)
			res = new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT, "FORBIDDEN: Won't serve ../ for security reasons.");

		try {
			result = this.expansionFile.getAssetFileDescriptor(path);
			if (DEBUG)
				Log.i(LOGTAG, "Retrieved " + path);
			if (DEBUG)
				Log.i(LOGTAG, result.toString());
			in = result.createInputStream();
		} catch (Exception e) {
			// throw new FileNotFoundException();
			if (DEBUG)
				Log.e(LOGTAG, "Failed to retrieve " + path);
			res = new Response(HTTP_INTERNALERROR, MIME_PLAINTEXT, "Failed to retrieve " + path);
		}

		try {
			if (res == null) {
				// Get MIME type from file name extension, if possible
				String mime = null;
				int dot = path.lastIndexOf('.');
				if (dot >= 0)
					mime = (String) this.mimeTypes.get(path.substring(dot + 1).toLowerCase());
				if (mime == null)
					mime = MIME_DEFAULT_BINARY;

				// Support (simple) skipping:
				long startFrom = 0;
				long endAt = -1;
				String range = header.getProperty("range");
				if (range != null) {
					if (range.startsWith("bytes=")) {
						range = range.substring("bytes=".length());
						int minus = range.indexOf('-');
						try {
							if (minus > 0) {
								startFrom = Long.parseLong(range.substring(0, minus));
								endAt = Long.parseLong(range.substring(minus + 1));
							}
						} catch (NumberFormatException nfe) {
						}
					}
				}

				// Change return code and add Content-Range header when skipping is requested
				long fileLen = (null != result) ? result.getLength() : 0;
				// System.out.println( String.format("file length: %d", fileLen));

				if (null != in) {
					if (range != null && startFrom >= 0) {
						if (startFrom >= fileLen) {
							res = new Response(HTTP_RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "");
							res.addHeader("Content-Range", "bytes 0-0/" + fileLen);
						} else {
							if (endAt < 0)
								endAt = fileLen - 1;
							long newLen = endAt - startFrom + 1;
							if (newLen < 0)
								newLen = 0;

							final long dataLen = newLen;
							// InputStream fis = new FileInputStream( f ) {
							// public int available() throws IOException { return (int)dataLen; }
							// };
							InputStream fis = in;
							fis.skip(startFrom);

							res = new Response(HTTP_PARTIALCONTENT, mime, fis);
							res.addHeader("Content-Length", "" + dataLen);
							res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLen);
						}
					} else {
						// res = new Response( HTTP_OK, mime, new FileInputStream( f ));
						res = new Response(HTTP_OK, mime, in);
						res.addHeader("Content-Length", "" + fileLen);

					}
				} else {
					res = new Response(HTTP_NOTFOUND, MIME_PLAINTEXT, "Failed to retrieve " + path);

				}
			}
		} catch (IOException ioe) {
			res = new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT, "FORBIDDEN: Reading file failed.");
		}

		res.addHeader("Accept-Ranges", "bytes"); // Announce that the file server accepts partial content requestes
		return res;
	}
}
