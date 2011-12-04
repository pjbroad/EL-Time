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

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

import com.fallenangelsguild.eltime.WebHelper.ApiException;

/**
 * Define a simple widget that shows the EL time and special day, if any.
 */
public class ELWidget extends AppWidgetProvider {
	private static final String APPTAG = "EL Time";
	private static final String PREFTAG = APPTAG+" LKG";
	private static final String FORCE = "force";
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
            int[] appWidgetIds) {
    	Log.d(APPTAG,"onUpdate() called");
        // To prevent any ANR timeouts, we perform the update in a service
        context.startService(new Intent(context, UpdateService.class));
        AlarmManager am=(AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent piUpdate=PendingIntent.getService(context, 0, new Intent(context, UpdateService.class), PendingIntent.FLAG_UPDATE_CURRENT);
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis()+30*1000, 60*1000, piUpdate);
    }
    
    /* (non-Javadoc)
	 * @see android.appwidget.AppWidgetProvider#onReceive(android.content.Context, android.content.Intent)
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
			NetworkInfo ni=intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
			if (ni.isConnected()) {
				Log.d(APPTAG,"Network is now connected");
				if (UpdateService.lkg.needrefresh) {
					Log.d(APPTAG,"LKG needs refresh, sending update");
			        Intent i=new Intent(context,UpdateService.class);
			        i.putExtra(FORCE, true);
			        context.startService(i);
				} else {
					Log.d(APPTAG,"LKG already refreshed, not sending update");
				}
			}
		}
		super.onReceive(context, intent);
	}



	private static class UpdateData {
    	public final static int DAYUNKNOWN=0;
    	public final static int DAYORDINARY=1;
    	public final static int DAYSPECIAL=2;
    	
    	long lastattempt=0; // ms since 1/1/1970
    	long lastupdate=0; // ms since 1/1/1970
    	long utc=1288091485; // s since 1/1/1970
    	int elt=4*3600+57*60; // s since 0:00 of the current EL day
    	String eldate="?"; // textual description of the date
    	String elday="?"; // name description of the (special) day
    	String eldaydesc=""; // description of the (special) day
		boolean needrefresh=true;
		int specialday=DAYUNKNOWN;
		
		public String toString() {
			return "last [a:"+(System.currentTimeMillis()-lastattempt)+", u:"+(System.currentTimeMillis()-lastupdate)+"], "+
			"utc="+utc+", elt="+elt+", elday="+elday.substring(0,Math.min(elday.length(),10))+", needrefresh="+needrefresh+", specialday="+specialday;
		}
    }

    public static class UpdateService extends Service {
    	private static final long UPDATETIME = 30*60*1000;
    	private static final long GRACETIME = 5*60*1000;
		private static UpdateData lkg=new UpdateData();

		@Override
        public void onStart(Intent intent, int startId) {
			Log.d(APPTAG,"UdpateService.onStart()");
            // Build the widget update for today
            RemoteViews updateViews = buildUpdate(this,intent.getBooleanExtra(FORCE, false));

            // Push update for this widget to the home screen
            ComponentName thisWidget = new ComponentName(this, ELWidget.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            manager.updateAppWidget(thisWidget, updateViews);
        }

        public RemoteViews buildUpdate(Context context, boolean force) {
			Log.d(APPTAG,"UpdateService.buildUpdate(context,"+force+")");
        	Resources res = context.getResources();
            RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget_word);

            updateViews.setTextViewText(R.id.definition, "Loading...");
            String prevDay=lkg.elday;
			try {
        		if (force || timeForNetUpdate())
        			doNetUpdate(context);
        	} catch (Exception e) {
        		Log.e(APPTAG,"Exception in net update",e);
        	}
        	String newDay=lkg.elday;
            
	        long lutc=System.currentTimeMillis()/1000;
	        long rldiff=lutc-lkg.utc; // seconds passed since elt
	        long eldiff=(long)(rldiff*60.0/60.475);
	        long nelt=lkg.elt+eldiff;
	        Log.d(APPTAG,"lkg: "+lkg.toString());
	        Log.d(APPTAG,"update: "+"rldiff="+rldiff+", eldiff="+eldiff+", nelt="+nelt);
	        if (nelt>6*60*60) {
            	lkg.needrefresh=true;
            	nelt%=(6*60*60);
            }

            try {
	            // Build an update that holds the updated widget contents
	            String cureltime=String.format("%d:%02d", nelt/(60*60),(nelt/60)%60);
	            updateViews.setTextViewText(R.id.word_title, "EL "+cureltime);
	            if (lkg.needrefresh)
		            updateViews.setTextViewText(R.id.definition, "?");
	            else
	            	updateViews.setTextViewText(R.id.definition, lkg.elday+".\n"+lkg.eldaydesc);
	            if (!prevDay.equalsIgnoreCase(newDay) && !newDay.equals("?") && !newDay.equalsIgnoreCase(res.getString(R.string.widget_ordinary_day)))
	            	postNotification(lkg.elday,lkg.elday+". "+lkg.eldaydesc);
            } catch (Exception e) {
            	Log.e(APPTAG,"Exception while building update",e);
            }
             // When user clicks on widget, launch to the EL site home page
            Intent defineIntent = new Intent(Intent.ACTION_VIEW, Uri.parse( res.getString(R.string.widget_el_page)));
            PendingIntent pendingIntent = PendingIntent.getActivity(context,
                        0 /* no requestCode */, defineIntent, 0 /* no flags */);
                updateViews.setOnClickPendingIntent(R.id.widget, pendingIntent);
            return updateViews;
        }

        private static void doNetUpdate(Context context) throws ApiException {
        	String timeSource=context.getString(R.string.widget_timesource_url);
    		String pageContent;
    		String [] as;

        	lkg.lastattempt=System.currentTimeMillis();
        	try {
                WebHelper.initialize(context);
                pageContent = WebHelper.getPageContent(timeSource, false);
		       	Log.d(APPTAG, "read: "+pageContent);
                if (!pageContent.startsWith("UTC:"))
                	throw new ApiException("Invalid response from "+timeSource+": "+pageContent);
            	as = pageContent.split("\n");
		        lkg.utc = Long.parseLong(as[0].split(" ")[1]);
		        String eltime = as[1].split(" ")[2];
		        lkg.elt = Integer.parseInt(eltime.split(":")[0])*3600+Integer.parseInt(eltime.split(":")[1])*60;
		        // now: utc is system time in seconds since epoch, elt is el time in seconds since start of day
		        lkg.eldate=as[2];

	            if (as[3].equalsIgnoreCase(context.getResources().getString(R.string.widget_ordinary_day))) {
	            	lkg.specialday=UpdateData.DAYORDINARY;
	            	lkg.elday=as[3];
	            	lkg.eldaydesc="";
	            } else {
	            	lkg.specialday=UpdateData.DAYSPECIAL;
	            	lkg.elday=as[4];
	            	StringBuffer sb=new StringBuffer();
	            	for (int i=5;i<as.length;i++)
	            		sb.append(as[i]).append("\n");
	            	lkg.eldaydesc=sb.toString();
	            }
		        lkg.lastupdate=System.currentTimeMillis();
		        lkg.needrefresh=false;
		        saveLKG(context);
	          } catch (Exception e) {
	        	  if (lkg.lastupdate==0) 
	        	  	lkg.needrefresh=true;
	        	  throw new ApiException("Error parsing response",e);
	          }
       }
private static void saveLKG(Context context) {
	//Log.d(APPTAG, "Saving LKG: "+lkg.toString());
	SharedPreferences settings = context.getSharedPreferences(PREFTAG, MODE_PRIVATE);
    SharedPreferences.Editor editor = settings.edit();
    editor.putLong("lastattempt", lkg.lastattempt);
    editor.putLong("lastupdate", lkg.lastupdate);
    editor.putLong("utc",lkg.utc);
    editor.putInt("elt", lkg.elt);
    editor.putString("eldate", lkg.eldate);
    editor.putString("elday",lkg.elday);
    editor.putString("eldaydesc", lkg.eldaydesc);
    editor.putBoolean("needrefresh", lkg.needrefresh);
    editor.putInt("specialday", lkg.specialday);
    editor.commit();
}

private static void loadLKG(Context context) {
	//Log.d(APPTAG,"Loading LKG: pre "+lkg.toString());
	SharedPreferences settings = context.getSharedPreferences(PREFTAG, MODE_PRIVATE);
	lkg.lastattempt=settings.getLong("lastattempt", 0);
	lkg.lastupdate=settings.getLong("lastupdate",0);
	lkg.utc=settings.getLong("utc", 1288091485);
    lkg.elt=settings.getInt("elt", 4*3600+57*60);
    lkg.eldate=settings.getString("eldate", "?");
    lkg.elday=settings.getString("elday","?");
    lkg.eldaydesc=settings.getString("eldaydesc", "");
    lkg.needrefresh=settings.getBoolean("needrefresh", true);
    lkg.specialday=settings.getInt("specialday",UpdateData.DAYUNKNOWN);
	//Log.d(APPTAG,"Loading LKG: post "+lkg.toString());
}


// Typical output:
//        	UTC: 1287125021
//        	Game time: 3:23
//        	Today is the 2nd Mortun, in the month of Vespia, in the year 0024, Age of the Eternals.
//        	Today is a special day:
//        	Day of Magic
//        	Today you gain twice the magic experience.
    // or
//        	UTC: 1287149227
//        	Game time: 4:2
//        	Today is the 2nd Zarun, in the month of Vespia, in the year 0024, Age of the Eternals.
//        	Just an ordinary day
            

		private static boolean timeForNetUpdate() {
        	long now=System.currentTimeMillis();
			if (now-lkg.lastattempt<GRACETIME)
				return false;
			if (lkg.needrefresh)
				return true;
			if (now-lkg.lastupdate<UPDATETIME)
				return false;
			return true;
		}

		private void postNotification(String day, CharSequence desc) {
        	NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        	int icon = R.drawable.ic_stat_notify;
        	CharSequence tickerText = day;
        	long when = System.currentTimeMillis();
        	Notification notif = new Notification(icon, tickerText, when);
        	Context context = getApplicationContext();
        	CharSequence contentTitle = "Special day";
        	CharSequence contentText = desc;
        	Intent notificationIntent = new Intent(context, UpdateService.class);
        	PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        	notif.flags |= Notification.FLAG_AUTO_CANCEL;
        	notif.defaults |= Notification.DEFAULT_ALL;
        	
        	notif.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        	nm.notify(1, notif);
		}
        
        @Override
        public IBinder onBind(Intent intent) {
            // We don't need to bind to this service
            return null;
        }

		/* (non-Javadoc)
		 * @see android.app.Service#onCreate()
		 */
		@Override
		public void onCreate() {
			Log.d(APPTAG,"onCreate() called");
			super.onCreate();
			loadLKG(getApplicationContext());
		}
    }
}
