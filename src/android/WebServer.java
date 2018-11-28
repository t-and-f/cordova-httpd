package com.rjfun.cordova.httpd;

import xapk.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Arrays;

import android.content.Context;
import android.util.Log;

public class WebServer extends NanoHTTPD
{
	private final String LOGTAG = "CorHTTPD";

	private AndroidFile myRootDir = null;

	private XAPKZipResourceFile expansionFile = null;

	public WebServer(InetSocketAddress localAddr, AndroidFile wwwroot, Context ctx) throws IOException {
		super(localAddr, wwwroot);
		myRootDir = wwwroot;
		Log.i( LOGTAG, "OBB dir: " + ctx.getObbDir());
		// Retrieve the expansion file.
		this.expansionFile = XAPKExpansionSupport.getAPKExpansionZipFile(ctx, 1, 1);
		if (null != this.expansionFile) {
			Log.i( LOGTAG, "Expansion file: " + this.expansionFile.toString() );
		}
	}

	public WebServer(int port, AndroidFile wwwroot, Context ctx ) throws IOException {
		super(port, wwwroot);
		myRootDir = wwwroot;
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
			Log.i( LOGTAG, method + " '" + uri + "' is in target folder" );
		}
		else {
			Log.i( LOGTAG, method + " '" + uri + "' " );
		}

		/*
			Enumeration e = header.propertyNames();
			while ( e.hasMoreElements())
			{
				String value = (String)e.nextElement();
				Log.i( LOGTAG, "  HDR: '" + value + "' = '" + header.getProperty( value ) + "'" );
			}
			
			e = parms.propertyNames();
			while ( e.hasMoreElements())
			{
				String value = (String)e.nextElement();
				Log.i( LOGTAG, "  PRM: '" + value + "' = '" + parms.getProperty( value ) + "'" );
			}
			
			e = files.propertyNames();
			while ( e.hasMoreElements())
			{
				String value = (String)e.nextElement();
				Log.i( LOGTAG, "  UPLOADED: '" + value + "' = '" + files.getProperty( value ) + "'" );
			}
		*/
		return serveFile( uri, header, myRootDir, true );
	}
}

