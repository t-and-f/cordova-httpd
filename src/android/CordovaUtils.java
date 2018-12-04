package com.rjfun.cordova.httpd;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.content.pm.Signature;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageInfo;

import org.xmlpull.v1.XmlPullParserException;
import android.util.Log;

public class CordovaUtils {
    private final String LOGTAG = "CordovaUtils";
    // restrict the set of preference names we are accepting
    private List<String> supportedKeys = new ArrayList(Arrays.asList("XAPK_PUBLIC_KEY"));

    private final String preferenceTag = "preference";

    // Find out if we're in a debug or release build.
	// Debug builds can't get downloads from Google Play. We need to know that
	// before starting DownloaderClientMarshaller.
	// Thanks to Omar Rehman: http://stackoverflow.com/a/11535593/1136569.
	private final String DEBUG_DN = "CN=Android Debug";

    public Map loadConfigFromXml(Resources res, String packageName) {
        //
        int configXmlResourceId = res.getIdentifier("config", "xml", packageName);
		
        XmlResourceParser xrp = res.getXml(configXmlResourceId);

        Map configs = new HashMap();

        //
        // walk the config.xml tree and save all <preference> tags we want
        //
        try {
            xrp.next();
            while (xrp.getEventType() != XmlResourceParser.END_DOCUMENT) {
                if (preferenceTag.equals(xrp.getName())) {
                    String key = matchSupportedKeyName(xrp.getAttributeValue(null, "name"));
                    if (key != null) {
                        configs.put(key, xrp.getAttributeValue(null, "value"));
                    }
                }
                xrp.next();
            }
        } catch (XmlPullParserException ex) {
            Log.e(LOGTAG, ex.toString());
        } catch (IOException ex) {
            Log.e(LOGTAG, ex.toString());
        }

        return configs;
    }

    private String matchSupportedKeyName(String testKey) {
        //
        // If key matches, return the version with correct casing.
        // If not, return null.
        // O(n) here is okay because this is a short list of just a few items
        for (String realKey : supportedKeys) {
            if (realKey.equalsIgnoreCase(testKey)) {
                return realKey;
            }
        }
        return null;
    }

    public boolean signatureIsDebug(Context ctx) {
		boolean isDebug = false;
		try {
			PackageInfo pinfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),
					PackageManager.GET_SIGNATURES);
			Signature signatures[] = pinfo.signatures;
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			for (int i = 0; i < signatures.length; i++) {
				ByteArrayInputStream stream = new ByteArrayInputStream(signatures[i].toByteArray());
				X509Certificate cert = (X509Certificate) cf.generateCertificate(stream);
				isDebug = cert.getSubjectX500Principal().toString().contains(DEBUG_DN);
				if (isDebug)
					break;
			}
		} catch (NameNotFoundException e) {
			// The "isDebug" variable will remain false.
		} catch (CertificateException e) {
			// The "isDebug" variable will remain false.
		}
		return isDebug;
	}
}