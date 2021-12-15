package com.abdulkarimalbaik.dev.alarmsystem.Helper;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;

import com.abdulkarimalbaik.dev.alarmsystem.R;

public class NotificationHelper extends ContextWrapper {

    private static final String ABD_CHANNEL_ID = "com.example.asus.alarmsystem.ABDULKARIM";
    private static final String ABD_CHANNEL_NAME = "Alarm System";

    private NotificationManager manager;

    public NotificationHelper(Context base) {
        super(base);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){  //only working this function if API is 26 or higher

            createChannel();
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void createChannel() {

        NotificationChannel abdChannel = new NotificationChannel(ABD_CHANNEL_ID,
                ABD_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT);  //shows everywhere, makes noise, but does not visually intrude.

        abdChannel.enableLights(false);  //Sets whether notifications posted to this channel should display notification lights, on devices that support that feature.
        abdChannel.enableVibration(true);  //Sets whether notification posted to this channel should vibrate. The vibration pattern can
        abdChannel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PRIVATE);  //Show this notification on all lockscreens, but conceal sensitive or private information on secure lockscreens.

        getManager().createNotificationChannel(abdChannel);  //Create notification channel in Notification Service in android System
    }

    public NotificationManager getManager() {

        if (manager == null)
            manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

        return manager;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public android.app.Notification.Builder getAlarmSystemChannelNotification(String title , String  body ,
                                                                        PendingIntent contentIntent ,
                                                                        Uri soundUri){
        return new android.app.Notification.Builder(getApplicationContext() , ABD_CHANNEL_ID)
                .setContentIntent(contentIntent)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.mipmap.ic_alarm_system)
                .setSound(soundUri)
                .setAutoCancel(false); //Make this notification automatically dismissed when the user touches it.

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public android.app.Notification.Builder getAlarmSystemChannelNotification(String title , String  body ,
                                                                        Uri soundUri){
        return new android.app.Notification.Builder(getApplicationContext() , ABD_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.mipmap.ic_alarm_system)
                .setSound(soundUri)
                .setAutoCancel(false);

    }
}
