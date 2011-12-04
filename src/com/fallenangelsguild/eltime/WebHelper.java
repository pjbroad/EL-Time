/*
 * Copyright (C) 2010 Usl of >FA<
 * Licensed under the Apache License, Version 2.0 (the "License");
 * 
 * This is derivative work of code originally distributed under the following license:
 * 
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fallenangelsguild.eltime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

/**
 * Helper methods to simplify talking with a web EL-time service.
 *  Before making any requests, you should call
 * {@link #initialize(Context)} to generate a User-Agent string based on
 * your application package name and version.
 */
public class WebHelper {
	private static Context c=null;
    private static final String TAG = "WebHelper";


    /**
     * {@link StatusLine} HTTP status code when no server error has occurred.
     */
    private static final int HTTP_STATUS_OK = 200;

    /**
     * Shared buffer used by {@link #getUrlContent(String)} when reading results
     * from an API request.
     */
    private static byte[] sBuffer = new byte[512];

    /**
     * User-agent string to use when making requests. Should be filled using
     * {@link #initialize(Context)} before making any other calls.
     */
    private static String sUserAgent = null;

    /**
     * Thrown when there were problems contacting the remote API server, either
     * because of a network error, or the server returned a bad status code.
     */
    public static class ApiException extends Exception {
        public ApiException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }

        public ApiException(String detailMessage) {
            super(detailMessage);
        }
    }

    /**
     * Prepare the internal User-Agent string for use. This requires a
     * {@link Context} to pull the package name and version number for this
     * application.
     */
    public static void initialize(Context context) {
    	c=context;
        try {
            // Read package name and version number from manifest
            PackageManager manager = c.getPackageManager();
            PackageInfo info = manager.getPackageInfo(c.getPackageName(), 0);
            sUserAgent = String.format(c.getString(R.string.template_user_agent),
                    info.packageName, info.versionName);

        } catch(NameNotFoundException e) {
            Log.e(TAG, "Couldn't find package information in PackageManager", e);
        }
    }

    public static String getPageContent(String page, boolean flag) 
    throws ApiException {
        if (c == null) {
            throw new ApiException("Must initialize() first");
        }
// Typical output:
//    	UTC: 1287125021
//    	Game time: 3:23
//    	Today is the 2nd Mortun, in the month of Vespia, in the year 0024, Age of the Eternals.
//    	Today is a special day:
//    	Day of Magic
//    	Today you gain twice the magic experience.
// or
//    	UTC: 1287149227
//    	Game time: 4:2
//    	Today is the 2nd Zarun, in the month of Vespia, in the year 0024, Age of the Eternals.
//    	Just an ordinary day

			String content = getUrlContent(page);
			return content;
//			"UTC: 1287125021\n"+
//			"Game time: 3:23\n"+
//			"Today is the 2nd Mortun, in the month of Vespia, in the year 0024, Age of the Eternals.\n"+
//			"Today is a special day:\n"+
//			"Day of Magic\n"+
//			"Today you gain twice the magic experience.";
    }

    /**
     * Pull the raw text content of the given URL. This call blocks until the
     * operation has completed, and is synchronized because it uses a shared
     * buffer {@link #sBuffer}.
     *
     * @param url The exact URL to request.
     * @return The raw content returned by the server.
     * @throws ApiException If any connection or server error occurs.
     */
    protected static synchronized String getUrlContent(String url)  throws ApiException {
        if (c == null) {
            throw new ApiException("Must initialize() first");
        }

        // Create client and set our specific user-agent string
        HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet(url);
        request.setHeader("User-Agent", sUserAgent);

        try {
            HttpResponse response = client.execute(request);

            // Check if server response is valid
            StatusLine status = response.getStatusLine();
            if (status.getStatusCode() != HTTP_STATUS_OK) {
                throw new ApiException("Invalid response from server: " +
                        status.toString());
            }

            // Pull content stream from response
            HttpEntity entity = response.getEntity();
            InputStream inputStream = entity.getContent();

            ByteArrayOutputStream content = new ByteArrayOutputStream();

            // Read response into a buffered stream
            int readBytes = 0;
            while ((readBytes = inputStream.read(sBuffer)) != -1) {
                content.write(sBuffer, 0, readBytes);
            }

            // Return result from buffered stream
            return new String(content.toByteArray());
        } catch (IOException e) {
            throw new ApiException("Problem communicating with API", e);
        }
    }
}
