package com.example.localmusicplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        MainActivity activity = MainActivity.getInstance();

        if (action != null) {
            if ("PREVIOUS".equals(action)) {
                if (activity != null) {
                    activity.playPrev();
                }
            } else if ("PLAYPAUSE".equals(action)) {
                if (activity != null) {
                    activity.playPause();
                }
            } else if ("NEXT".equals(action)) {
                if (activity != null) {
                    activity.playNext();
                }
            }
        }
    }
}