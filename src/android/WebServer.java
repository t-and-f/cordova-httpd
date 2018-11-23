package com.rjfun.cordova.httpd;

import java.io.IOException;
import java.net.InetSocketAddress;

import android.util.Log;

public class WebServer extends NanoHTTPD
{
	private final String LOGTAG = "CorHTTPD";

	private String myRootDir = "";

	public WebServer(InetSocketAddress localAddr, AndroidFile wwwroot) throws IOException {
		super(localAddr, wwwroot);
		myRootDir = wwwroot;
	}

	public WebServer(int port, AndroidFile wwwroot ) throws IOException {
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
		Log.i( LOGTAG, method + " '" + uri + "' " );
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
