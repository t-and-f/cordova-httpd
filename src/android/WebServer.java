package com.rjfun.cordova.httpd;

import xapk.*;

import org.apache.cordova.CordovaInterface;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Arrays;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;


public class WebServer extends NanoHTTPD
{
	private final String LOGTAG = "CorHTTPD";

	private CordovaInterface cordova = null;

	private AndroidFile myRootDir = null;

	private XAPKZipResourceFile expansionFile = null;

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

		Log.i( LOGTAG, "OBB dir: " + ctx.getObbDir());
		// Retrieve the expansion file.
		this.expansionFile = XAPKExpansionSupport.getAPKExpansionZipFile(ctx, 1, 1);
		if (null != this.expansionFile) {
			Log.i( LOGTAG, "Expansion file: " + this.expansionFile.toString() );

			Object[] listing = this.expansionFile.getAllEntries();
			Log.i( LOGTAG, "Expansion file content: " + Arrays.toString(listing) );
		}
	}

	/*
	 * (By default, this delegates to serveFile() and allows directory listing.)
	 *
	 * @param uri	Percent-decoded URI without parameters, for example "/index.cgi"
	 * @param method	"GET", "POST" etc.
	 * @param parms	Parsed, percent decoded parameters from URI and, in case of POST, data.
	 * @param header	Header entries, percent decoded
	 * @return HTTP response, see class Response for details
	 */
	@SuppressWarnings("rawtypes")
	public Response serve( String uri, String method, Properties header, Properties parms, Properties files )
	{
		FileSystem fileSystem = FileSystems.getDefault();
		PathMatcher matcher = fileSystem.getPathMatcher("glob:/www/assets/**");
		
		if (matcher.matches(Paths.get(uri))) {
			String path = (uri.split("/www/assets/"))[1];
			AssetFileDescriptor result;
			Log.i( LOGTAG, method + " '" + uri + "' is in target folder" );
			
			try {
				result = this.expansionFile.getAssetFileDescriptor(path);
				Log.i( LOGTAG, "Retrieved " + path);
				
			} catch (Exception e) {
				// throw new FileNotFoundException();
				Log.e( LOGTAG, "Failed to retrieve " + path);
			}
		}
		else {
			Log.i( LOGTAG, method + " '" + uri + "' " );
		}

		return serveFile( uri, header, myRootDir, true );
	}
}

