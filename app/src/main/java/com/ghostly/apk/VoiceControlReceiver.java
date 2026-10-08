package com.ghostly.apk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class VoiceControlReceiver extends BroadcastReceiver {
    public static final String ACTION_MUTE = "com.ghostly.apk.VOICE_MUTE";
    public static final String ACTION_STOP = "com.ghostly.apk.VOICE_STOP";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        Intent i = new Intent(context, VoiceChatService.class);
        if (ACTION_MUTE.equals(action)) {
            i.setAction(VoiceChatService.ACTION_TOGGLE_MUTE);
        } else if (ACTION_STOP.equals(action)) {
            i.setAction(VoiceChatService.ACTION_STOP);
        } else {
            return;
        }
        context.startService(i);
    }
}
