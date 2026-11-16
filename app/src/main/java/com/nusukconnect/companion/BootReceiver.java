package com.nusukconnect.companion;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) { DailyReceiver.schedule(context); }
}
