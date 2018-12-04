package com.rjfun.cordova.httpd;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParserException;

import android.util.Log;

class CordovaUtils {
    private final String LOGTAG = "CordovaUtils";
    // restrict the set of preference names we are accepting
    private List<String> supportedKeys = new ArrayList(Arrays.asList("XAPK_PUBLIC_KEY"));

    private final String preferenceTag = "preference";

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
}