package com.rjfun.cordova.httpd;

import xapk.*;

import org.apache.cordova.CordovaInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Properties;
import java.util.StringTokenizer;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

public class WebServer extends NanoHTTPD {
	private final String LOGTAG = "CorHTTPD";

	private CordovaInterface cordova = null;

	private AndroidFile myRootDir = null;

	private XAPKZipResourceFile expansionFile = null;

	/**
	 * Hashtable mapping (String)FILENAME_EXTENSION -> (String)MIME_TYPE
	 */
	@SuppressWarnings("rawtypes")
	private Hashtable mimeTypes = new Hashtable();
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

	public WebServer(InetSocketAddress localAddr, AndroidFile wwwroot, CordovaInterface cordova) throws IOException {
		super(localAddr, wwwroot);
		this.init(wwwroot, cordova);
	}

	public WebServer(int port, AndroidFile wwwroot, CordovaInterface cordova) throws IOException {
		super(port, wwwroot);
		this.init(wwwroot, cordova);
	}

	private void init(AndroidFile wwwroot, CordovaInterface cordova) throws IOException {
		this.myRootDir = wwwroot;
		this.cordova = cordova;
		Context ctx = cordova.getActivity().getApplicationContext();

		Log.i(LOGTAG, "OBB dir: " + ctx.getObbDir());
		// Retrieve the expansion file.
		this.expansionFile = XAPKExpansionSupport.getAPKExpansionZipFile(ctx, 1, 1);
		if (null != this.expansionFile) {
			Log.i(LOGTAG, "Expansion file: " + this.expansionFile.toString());

			Object[] listing = this.expansionFile.getAllEntries();
			Log.i(LOGTAG, "Expansion file content: " + Arrays.toString(listing));
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
		String basePath = "/www/assets/";

		if (uri.startsWith(basePath)) {
			String path = (uri.split(basePath))[1];
			Log.i(LOGTAG, method + " '" + uri + "' is in target folder");
			return this.serveFromAPK(path, header, myRootDir);

		} else {
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
			in = result.createInputStream();
			Log.i(LOGTAG, "Retrieved " + path);
			Log.i(LOGTAG, "Descriptor: " + result.toString());

		} catch (Exception e) {
			// throw new FileNotFoundException();
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

